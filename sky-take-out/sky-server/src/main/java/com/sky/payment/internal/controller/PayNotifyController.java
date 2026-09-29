package com.sky.payment.internal.controller;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.payment.api.PaymentCallbackCommand;
import com.sky.payment.internal.PaymentCallbackService;
import com.sky.observability.SafeLogIdentifier;
import com.sky.properties.WeChatProperties;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.math.BigInteger;
import org.springframework.util.StringUtils;
import java.util.Base64;
import java.util.HashMap;

/**
 * 支付回调相关接口
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private PaymentCallbackService paymentCallbackService;

    @RequestMapping("/paySuccess")
    public void paySuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String body = readData(request);
        if (!StringUtils.hasText(body) || !verifySignature(request, body)) {
            response.setStatus(400);
            return;
        }
        PaymentCallbackCommand command;
        try {
            JSONObject payment = JSON.parseObject(decryptData(body));
            if (!"SUCCESS".equalsIgnoreCase(payment.getString("trade_state"))) {
                responseToWeixin(response);
                return;
            }
            JSONObject amount = payment.getJSONObject("amount");
            String orderNumber = payment.getString("out_trade_no");
            String transactionId = payment.getString("transaction_id");
            if (!StringUtils.hasText(weChatProperties.getMchid()) || !weChatProperties.getMchid().equals(payment.getString("mchid"))
                    || !StringUtils.hasText(weChatProperties.getAppid()) || !weChatProperties.getAppid().equals(payment.getString("appid"))
                    || !StringUtils.hasText(orderNumber) || orderNumber.length() > 50
                    || !StringUtils.hasText(transactionId) || transactionId.length() > 64
                    || amount == null || !"CNY".equals(amount.getString("currency"))) {
                response.setStatus(400);
                return;
            }
            // Reject fractional/overflow minor units instead of silently truncating JSON numbers.
            int total = amount.getBigDecimal("total").intValueExact();
            if (total < 0) {
                response.setStatus(400);
                return;
            }
            command = new PaymentCallbackCommand(orderNumber, transactionId, total);
        } catch (Exception invalidPayload) {
            // Exception messages from JSON/AES may contain input; never log callback material.
            log.warn("Invalid verified payment callback payload");
            response.setStatus(400);
            return;
        }
        try {
            paymentCallbackService.handle(command);
            responseToWeixin(response);
        } catch (Exception failure) {
            log.error("Payment callback transaction failed");
            response.setStatus(500);
        }
    }

    /**
     * 退款成功回调
     *
     * @param request
     * @param response
     */
    @RequestMapping("/refundSuccess")
    public void refundSuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        //读取数据
        String body = readData(request);
        log.info("收到退款成功回调，bodyLength={}", body == null ? null : body.length());

        try {
            // 验证签名
            boolean signatureValid = verifySignature(request, body);
            if (!signatureValid) {
                log.error("退款回调签名验证失败");
                response.setStatus(400);
                return;
            }

            //数据解密
            String plainText = decryptData(body);

            JSONObject jsonObject = JSON.parseObject(plainText);
            String outTradeNo = jsonObject.getString("out_trade_no");//商户订单号
            String outRefundNo = jsonObject.getString("out_refund_no");//商户退款单号
            String refundId = jsonObject.getString("refund_id");//微信退款单号
            String refundStatus = jsonObject.getString("refund_status");//退款状态
            String successTime = jsonObject.getString("success_time");//退款成功时间
            
            // 解析退款金额
            JSONObject amount = jsonObject.getJSONObject("amount");
            Integer total = amount != null ? amount.getInteger("total") : null;
            Integer from = amount != null ? amount.getInteger("from") : null;
            
            // 解析用户收款账户
            log.info("退款回调解析完成，outTradeNo={}, outRefundNo={}, refundId={}, refundStatus={}, refundAmount={}分",
                    SafeLogIdentifier.forLog(outTradeNo, 50),
                    maskValue(SafeLogIdentifier.forLog(outRefundNo, 64), 6),
                    maskValue(SafeLogIdentifier.forLog(refundId, 64), 6),
                    SafeLogIdentifier.forLog(refundStatus, 32),
                    total);

            // 幂等性处理：退款成功无需额外处理
            log.info("退款回调处理完成，outTradeNo={}", SafeLogIdentifier.forLog(outTradeNo, 50));

            //给微信响应
            responseToWeixin(response);

        } catch (Exception e) {
            log.error("退款回调处理异常，errorType={}", e.getClass().getSimpleName());
            response.setStatus(500);
        }
    }

    /**
     * 验证微信支付回调签名
     * 
     * @param request HTTP 请求
     * @param body 请求体
     * @return 签名是否有效
     */
    private boolean verifySignature(HttpServletRequest request, String body) {
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String signature = request.getHeader("Wechatpay-Signature");
        String serialNumber = request.getHeader("Wechatpay-Serial");
        String signType = request.getHeader("Wechatpay-Signature-Type");
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature) || !StringUtils.hasText(serialNumber)
                || nonce.length() > 128 || nonce.contains("\n") || nonce.contains("\r")
                || (signType != null && !"WECHATPAY2-SHA256-RSA2048".equals(signType))) {
            return false;
        }
        try {
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (seconds < now - 300 || seconds > now + 300) {
                return false;
            }
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(getPlatformPublicKey(serialNumber));
            verifier.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception invalidSignature) {
            return false;
        }
    }

    private java.security.PublicKey getPlatformPublicKey(String serialNumber) throws Exception {
        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        try (FileInputStream input = new FileInputStream(weChatProperties.getWeChatPayCertFilePath())) {
            java.security.cert.X509Certificate certificate = (java.security.cert.X509Certificate) factory.generateCertificate(input);
            certificate.checkValidity();
            if (!certificate.getSerialNumber().equals(new BigInteger(serialNumber, 16))) {
                throw new IllegalArgumentException("Unknown platform certificate serial");
            }
            return certificate.getPublicKey();
        }
    }

    /**
     * 隐藏敏感信息
     *
     * @param value                待隐藏的字符串
     * @param keepSuffixLength     保留的后缀长度
     * @return 隐藏后的字符串
     */
    private String maskValue(String value, int keepSuffixLength) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (keepSuffixLength <= 0 || value.length() <= keepSuffixLength) {
            return "***";
        }
        return "***" + value.substring(value.length() - keepSuffixLength);
    }

    /**
     * 读取数据
     *
     * @param request
     * @return
     * @throws Exception
     */
    private String readData(HttpServletRequest request) throws Exception {
        BufferedReader reader = request.getReader();
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    /**
     * 数据解密
     *
     * @param body
     * @return
     * @throws Exception
     */
    private String decryptData(String body) throws Exception {
        JSONObject resultObject = JSON.parseObject(body);
        JSONObject resource = resultObject.getJSONObject("resource");
        String ciphertext = resource.getString("ciphertext");
        String nonce = resource.getString("nonce");
        String associatedData = resource.getString("associated_data");

        AesUtil aesUtil = new AesUtil(weChatProperties.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        //密文解密
        String plainText = aesUtil.decryptToString(associatedData.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8),
                ciphertext);

        return plainText;
    }

    /**
     * 给微信响应
     * @param response
     */
    private void responseToWeixin(HttpServletResponse response) throws Exception{
        response.setStatus(200);
        HashMap<Object, Object> map = new HashMap<>();
        map.put("code", "SUCCESS");
        map.put("message", "SUCCESS");
        response.setHeader("Content-type", ContentType.APPLICATION_JSON.toString());
        response.getOutputStream().write(JSONUtils.toJSONString(map).getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }
}
