package com.sky.payment;

import com.sky.payment.internal.controller.PayNotifyController;
import com.sky.properties.WeChatProperties;
import com.sky.entity.PaymentCallbackLog;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.api.event.OrderPaidMessage;
import com.sky.payment.internal.PaymentCallbackService;
import com.sky.payment.internal.persistence.PaymentCallbackLogMapper;
import com.sky.payment.internal.persistence.PaymentEventMapper;
import com.alibaba.fastjson.JSON;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PayNotifyControllerTest {
    @TempDir Path temp;
    private final PayNotifyController controller = new PayNotifyController();
    private final PaymentCallbackLogMapper callbacks = mock(PaymentCallbackLogMapper.class);
    private final PaymentEventMapper events = mock(PaymentEventMapper.class);
    private final OrderApplicationService orders = mock(OrderApplicationService.class);
    private final String payment = "{\"out_trade_no\":\"callback-order\",\"transaction_id\":\"wx-98001\",\"trade_state\":\"SUCCESS\",\"mchid\":\"merchant\",\"appid\":\"app\",\"amount\":{\"total\":1234,\"currency\":\"CNY\"}}";

    @BeforeEach
    void setUp() throws Exception {
        WeChatProperties properties = new WeChatProperties();
        properties.setWeChatPayCertFilePath(CallbackCryptoFixture.certificate(temp).toString());
        properties.setApiV3Key(CallbackCryptoFixture.AES_KEY);
        properties.setMchid("merchant");
        properties.setAppid("app");
        // Merchant certificate serial is deliberately unrelated to the platform certificate.
        properties.setMchSerialNo("merchant-serial");
        ReflectionTestUtils.setField(controller, "weChatProperties", properties);
        ReflectionTestUtils.setField(controller, "paymentCallbackService", new PaymentCallbackService(callbacks, orders, events));
        when(callbacks.insert(any())).thenReturn(1);
        when(orders.completeVerifiedPayment("callback-order", new BigDecimal("12.34")))
                .thenReturn(new OrderPaidMessage(98001L, "callback-order", 7L, new BigDecimal("12.34")));
        when(events.insertPending(anyString(), anyString(), anyLong(), anyString(), anyString())).thenReturn(1);
    }

    @ParameterizedTest
    @CsvSource({"Wechatpay-Timestamp,invalid", "Wechatpay-Timestamp,1", "Wechatpay-Nonce,' '",
            "Wechatpay-Serial,' '", "Wechatpay-Signature,not-base64"})
    void invalidVerificationMetadataReturnsBadRequestBeforeBusinessHandling(String header, String value) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"resource\":{\"ciphertext\":\"secret-ciphertext\"}}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Wechatpay-Timestamp", Long.toString(Instant.now().getEpochSecond()));
        request.addHeader("Wechatpay-Nonce", "nonce");
        request.addHeader("Wechatpay-Serial", "123");
        request.addHeader("Wechatpay-Signature", "invalid");
        request.removeHeader(header);
        request.addHeader(header, value);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.paySuccessNotify(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(callbacks, orders, events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"timestamp", "future", "nonce", "serial", "signature", "body", "missing-serial"})
    void rejectsCryptographicallySignedButInvalidOrTamperedMetadataWithoutMutation(String invalid) throws Exception {
        MockHttpServletRequest request = signed(payment);
        if (invalid.equals("timestamp") || invalid.equals("future")) {
            String timestamp = Long.toString(Instant.now().getEpochSecond() + (invalid.equals("future") ? 600 : -600));
            replace(request, "Wechatpay-Timestamp", timestamp);
            replace(request, "Wechatpay-Signature", CallbackCryptoFixture.signature(timestamp, "nonce", request.getContentAsString()));
        } else if (invalid.equals("nonce")) {
            replace(request, "Wechatpay-Nonce", "tampered");
        } else if (invalid.equals("serial")) {
            replace(request, "Wechatpay-Serial", "DEADBEEF");
        } else if (invalid.equals("signature")) {
            String signature = request.getHeader("Wechatpay-Signature");
            replace(request, "Wechatpay-Signature", (signature.startsWith("A") ? "B" : "A") + signature.substring(1));
        } else if (invalid.equals("body")) {
            request.setContent((request.getContentAsString() + " ").getBytes(StandardCharsets.UTF_8));
        } else {
            request.removeHeader("Wechatpay-Serial");
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.paySuccessNotify(request, response);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(callbacks, orders, events);
    }

    @Test
    void validSignedEncryptedPaymentKeepsSuccessAcknowledgmentAndPersistsOnlySafeFields() throws Exception {
        MockHttpServletRequest request = signed(payment);
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.paySuccessNotify(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(JSON.parseObject(response.getContentAsString()).getString("code")).isEqualTo("SUCCESS");
        assertThat(JSON.parseObject(response.getContentAsString()).getString("message")).isEqualTo("SUCCESS");
        verify(callbacks).insert(argThat(callback -> callback.getRawCallbackData() == null && callback.getDecryptedData() == null
                && "callback-order".equals(callback.getOutTradeNo()) && "wx-98001".equals(callback.getTransactionId())));
        verify(orders).completeVerifiedPayment("callback-order", new BigDecimal("12.34"));
        verify(events).insertPending(anyString(), eq("wx-98001"), eq(98001L), eq("ORDER_PAID:98001"), anyString());
    }

    @Test
    void committedDuplicateAcknowledgesSuccessWithoutOrderOrEventEffects() throws Exception {
        when(callbacks.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(callbacks.getByTransactionIdForShare("wx-98001")).thenReturn(PaymentCallbackLog.builder()
                .outTradeNo("callback-order").transactionId("wx-98001").callbackStatus("SUCCESS").build());
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.paySuccessNotify(signed(payment), response);
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(orders, events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"merchant", "app", "CNY", "1234"})
    void rejectsWrongOwnershipCurrencyOrFractionalAmountBeforeMutation(String field) throws Exception {
        String replacement = field.equals("1234") ? "1234.5" : "wrong";
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.paySuccessNotify(signed(payment.replace(field, replacement)), response);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(callbacks, orders, events);
    }

    @Test
    void failuresNeverLogCiphertextOrDecryptedInput() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PayNotifyController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = signed("not-json-secret-plaintext");
            MockHttpServletResponse response = new MockHttpServletResponse();
            controller.paySuccessNotify(request, response);
            assertThat(response.getStatus()).isEqualTo(400);
            String ciphertext = JSON.parseObject(request.getContentAsString()).getJSONObject("resource").getString("ciphertext");
            assertThat(appender.list).allSatisfy(entry -> {
                assertThat(entry.getFormattedMessage()).doesNotContain(ciphertext, "not-json-secret-plaintext");
                assertThat(entry.getThrowableProxy()).isNull();
            });
            verifyNoInteractions(callbacks, orders, events);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void refundLogsNeverContainAccountPiiCiphertextOrRawTokens() throws Exception {
        String canary = "refund-account-canary@example.com";
        String refund = "{\"out_trade_no\":\"callback-order\",\"out_refund_no\":\"refund-1\","
                + "\"refund_id\":\"wx-refund-1\",\"refund_status\":\"SUCCESS\","
                + "\"amount\":{\"total\":1234},\"user_received_account\":{\"email\":\"" + canary
                + "\",\"token\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature\"}}";
        Logger logger = (Logger) LoggerFactory.getLogger(PayNotifyController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MockHttpServletRequest request = signed(refund);
        try {
            MockHttpServletResponse response = new MockHttpServletResponse();
            controller.refundSuccessNotify(request, response);
            String ciphertext = JSON.parseObject(request.getContentAsString())
                    .getJSONObject("resource").getString("ciphertext");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(appender.list).allSatisfy(entry -> {
                assertThat(entry.getFormattedMessage()).doesNotContain(canary, "eyJhbGciOiJIUzI1NiJ9", ciphertext);
                assertThat(entry.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void transactionFailureAsksWechatToRetry() throws Exception {
        when(events.insertPending(anyString(), anyString(), anyLong(), anyString(), anyString())).thenThrow(new IllegalStateException("failure"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.paySuccessNotify(signed(payment), response);
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void nonSuccessTradeAcknowledgesWithoutMutation() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.paySuccessNotify(signed(payment.replace("SUCCESS", "CLOSED")), response);
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(callbacks, orders, events);
    }

    private MockHttpServletRequest signed(String plaintext) throws Exception {
        String body = CallbackCryptoFixture.body(plaintext);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCharacterEncoding("UTF-8");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Wechatpay-Timestamp", timestamp);
        request.addHeader("Wechatpay-Nonce", "nonce");
        request.addHeader("Wechatpay-Serial", CallbackCryptoFixture.SERIAL);
        request.addHeader("Wechatpay-Signature", CallbackCryptoFixture.signature(timestamp, "nonce", body));
        return request;
    }

    private void replace(MockHttpServletRequest request, String header, String value) {
        request.removeHeader(header);
        request.addHeader(header, value);
    }
}
