package com.mall.controller.nofity;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mall.entity.PaymentCallbackLog;
import com.mall.mapper.PaymentCallbackLogMapper;
import com.mall.properties.WeChatProperties;
import com.mall.service.OrderService;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付回调相关接口
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private PaymentCallbackLogMapper paymentCallbackLogMapper;

    /**
     * 并发控制锁，防止微信重复回调
     */
    private final ConcurrentHashMap<String, Boolean> processingSet = new ConcurrentHashMap<>();

    /**
     * 支付成功回调
     *
     * @param request
     * @param response
     */
    @RequestMapping("/paySuccess")
    public void paySuccessNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        //读取数据
        String body = readData(request);
        log.info("收到支付成功回调，bodyLength={}", body == null ? null : body.length());

        if (body == null || body.isEmpty()) {
            log.error("支付回调请求体为空");
            response.setStatus(400);
            return;
        }

        try {
            JSONObject resultObject = JSON.parseObject(body);
            
            // 1. 验证签名（必须步骤）
            boolean signatureValid = verifySignature(request, body);
            if (!signatureValid) {
                log.error("微信支付回调签名验证失败");
                response.setStatus(400);
                return;
            }

            // 2. 数据解密
            String plainText = decryptData(body);
            log.info("支付回调数据解密成功");

            // 3. 解析核心字段
            JSONObject jsonObject = JSON.parseObject(plainText);
            String outTradeNo = jsonObject.getString("out_trade_no");
            String transactionId = jsonObject.getString("transaction_id");
            String timeStamp = jsonObject.getString("time_stamp");
            String successTime = jsonObject.getString("success_time");
            String tradeType = jsonObject.getString("trade_type");
            String tradeState = jsonObject.getString("trade_state");
            String tradeStateDesc = jsonObject.getString("trade_state_desc");
            String bankType = jsonObject.getString("bank_type");
            String attach = jsonObject.getString("attach");
            
            // 解析金额对象
            JSONObject amount = jsonObject.getJSONObject("amount");
            Integer total = amount != null ? amount.getInteger("total") : null;
            Integer payerTotal = amount != null ? amount.getInteger("payer_total") : null;
            String currency = amount != null ? amount.getString("currency") : null;
            String payerCurrency = amount != null ? amount.getString("payer_currency") : null;

            log.info("支付成功回调解析完成，outTradeNo={}, transactionId={}, tradeState={}, total={}分", 
                    outTradeNo, maskValue(transactionId, 6), tradeState, total);

            if (!"SUCCESS".equalsIgnoreCase(tradeState)) {
                log.warn("支付状态非成功，忽略处理，outTradeNo={}, tradeState={}", outTradeNo, tradeState);
                responseToWeixin(response);
                return;
            }

            if (outTradeNo == null || transactionId == null) {
                log.error("支付回调核心字段缺失，outTradeNo={}, transactionId={}", outTradeNo, transactionId);
                response.setStatus(400);
                return;
            }

            // 4. 幂等性检查：如果已处理过，直接返回成功
            PaymentCallbackLog existLog = paymentCallbackLogMapper.getByOutTradeNoAndTransactionId(outTradeNo, transactionId);
            if (existLog != null && "SUCCESS".equals(existLog.getCallbackStatus())) {
                log.warn("该支付回调已处理过，outTradeNo={}, transactionId={}, 避免重复处理", outTradeNo, maskValue(transactionId, 6));
                responseToWeixin(response);
                return;
            }

            // 5. 防止并发回调：使用内存锁
            String lockKey = "LOCK:" + outTradeNo;
            if (processingSet.putIfAbsent(lockKey, Boolean.TRUE) != null) {
                log.warn("该支付回调正在处理中，请稍后重试，outTradeNo={}", outTradeNo);
                responseToWeixin(response);
                return;
            }

            try {
                // 6. 记录回调日志（初始状态）
                PaymentCallbackLog callbackLog = PaymentCallbackLog.builder()
                        .outTradeNo(outTradeNo)
                        .transactionId(transactionId)
                        .callbackType("PAY_SUCCESS")
                        .callbackStatus("PROCESSING")
                        .rawCallbackData(body)
                        .decryptedData(plainText)
                        .handleCount(1)
                        .callbackTime(LocalDateTime.now())
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();
                
                if (existLog == null) {
                    paymentCallbackLogMapper.insert(callbackLog);
                    existLog = callbackLog;
                }

                // 7. 业务处理：修改订单状态、来单提醒
                orderService.paySuccessWithValidation(outTradeNo, transactionId, total != null ? total.doubleValue() / 100.0 : null);

                // 8. 更新回调日志为成功
                callbackLog.setCallbackStatus("SUCCESS");
                callbackLog.setHandleTime(LocalDateTime.now());
                callbackLog.setUpdateTime(LocalDateTime.now());
                if (existLog != null) {
                    callbackLog.setId(existLog.getId());
                    paymentCallbackLogMapper.update(callbackLog);
                }

                log.info("支付回调处理成功，outTradeNo={}, transactionId={}", outTradeNo, maskValue(transactionId, 6));

            } catch (Exception e) {
                log.error("支付回调处理失败，outTradeNo={}, transactionId={}", outTradeNo, maskValue(transactionId, 6), e);
                
                // 记录错误信息
                PaymentCallbackLog errorLog = PaymentCallbackLog.builder()
                        .outTradeNo(outTradeNo)
                        .transactionId(transactionId)
                        .callbackType("PAY_SUCCESS")
                        .callbackStatus("FAIL")
                        .errorMessage(e.getMessage())
                        .handleTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();
                
                if (existLog != null) {
                    errorLog.setId(existLog.getId());
                    paymentCallbackLogMapper.update(errorLog);
                } else {
                    paymentCallbackLogMapper.insert(errorLog);
                }
                
                throw e;
            } finally {
                processingSet.remove(lockKey);
            }

            // 9. 给微信响应
            responseToWeixin(response);

        } catch (Exception e) {
            log.error("支付回调处理异常", e);
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
            JSONObject userReceivedAccount = jsonObject.getJSONObject("user_received_account");
            if (userReceivedAccount != null) {
                log.info("退款到达用户账户：{}", userReceivedAccount.toJSONString());
            }

            log.info("退款回调解析完成，outTradeNo={}, outRefundNo={}, refundId={}, refundStatus={}, refundAmount={}分",
                    outTradeNo,
                    maskValue(outRefundNo, 6),
                    maskValue(refundId, 6),
                    refundStatus,
                    total);

            // 幂等性处理：退款成功无需额外处理
            log.info("退款回调处理完成，outTradeNo={}", outTradeNo);

            //给微信响应
            responseToWeixin(response);

        } catch (Exception e) {
            log.error("退款回调处理异常", e);
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
    private boolean verifySignature(HttpServletRequest request, String body) throws Exception {
        // 获取微信回调头中的签名相关信息
        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String signType = request.getHeader("Wechatpay-Signature-Type");
        String signature = request.getHeader("Wechatpay-Signature");
        String serialNumber = request.getHeader("Wechatpay-Serial");

        log.info("微信支付回调头信息：timestamp={}, nonce={}, signType={}, serialNo={}", 
                timestamp, nonce, signType, serialNumber);

        // 校验必要参数
        if (timestamp == null || nonce == null || signature == null) {
            log.error("缺少必要的签名相关头部");
            return false;
        }

        // 构建验签字符串
        String message = timestamp + "\n" + nonce + "\n" + body + "\n";

        // 使用 Java Signature 进行验签
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initVerify(getPlatformPublicKey(serialNumber));
        sign.update(message.getBytes(StandardCharsets.UTF_8));
        
        boolean verified = sign.verify(Base64.getDecoder().decode(signature));
        
        log.info("微信支付签名验证结果：{}", verified ? "通过" : "失败");
        return verified;
    }

    /**
     * 获取微信平台公钥
     * 根据证书序列号获取对应的公钥
     * 
     * @param serialNumber 证书序列号
     * @return 平台公钥
     */
    private java.security.PublicKey getPlatformPublicKey(String serialNumber) throws Exception {
        // TODO: 需要从微信商户平台下载的证书中读取公钥
        // 实际项目中应该：
        // 1. 从配置文件或数据库中获取证书路径
        // 2. 根据 serialNumber 查找对应的证书
        // 3. 从证书中提取公钥
        // 这里提供示例代码框架
        
        if (serialNumber == null || serialNumber.isEmpty()) {
            throw new IllegalArgumentException("微信平台证书序列号为空");
        }

        if (!serialNumber.equals(weChatProperties.getMchSerialNo())) {
            log.warn("回调证书序列号与配置不一致，serialNumber={}, configSerial={}",
                    serialNumber, weChatProperties.getMchSerialNo());
        }

        String certificatePath = weChatProperties.getWeChatPayCertFilePath();
        if (certificatePath == null || certificatePath.isEmpty()) {
            throw new IllegalArgumentException("微信平台证书路径未配置");
        }

        java.security.cert.CertificateFactory factory = java.security.cert.CertificateFactory.getInstance("X.509");
        try (FileInputStream fis = new FileInputStream(certificatePath)) {
            java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) factory.generateCertificate(fis);
            return cert.getPublicKey();
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
        String line = null;
        while ((line = reader.readLine()) != null) {
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(line);
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
