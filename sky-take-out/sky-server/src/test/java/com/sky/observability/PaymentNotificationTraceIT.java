package com.sky.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.messaging.outbox.OutboxEventMapper;
import com.sky.messaging.outbox.OutboxPublisher;
import com.sky.notification.api.OrderNotificationPort;
import com.sky.notification.internal.messaging.OrderEventConsumer;
import com.sky.payment.api.PaymentCallbackCommand;
import com.sky.payment.api.PaymentCallbackResult;
import com.sky.payment.internal.PaymentCallbackService;
import com.sky.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "sky.outbox.enabled=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentNotificationTraceIT extends IntegrationTestBase {
    private static final String SECRET_CANARY = "AO3-secret-canary-do-not-log";
    @Autowired private PaymentCallbackService payment;
    @Autowired private OutboxEventMapper events;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private OrderNotificationPort notification;
    @MockBean private ServerEndpointExporter serverEndpointExporter;
    private final ConcurrentLinkedQueue<String> notifications = new ConcurrentLinkedQueue<>();
    private final CapturedLogs logs = new CapturedLogs();
    private final List<Logger> loggers = List.of(
            (Logger) LoggerFactory.getLogger(PaymentCallbackService.class),
            (Logger) LoggerFactory.getLogger(OutboxPublisher.class),
            (Logger) LoggerFactory.getLogger(OrderEventConsumer.class));

    @BeforeEach
    void setUp() {
        // These are disposable Testcontainers tables; leave exactly one claimable event.
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM message_consumption WHERE business_key='AO3-ORDER-98103'");
        jdbc.update("DELETE FROM payment_callback_log WHERE out_trade_no='AO3-ORDER-98103'");
        jdbc.update("DELETE FROM orders WHERE id=98103");
        jdbc.update("INSERT INTO orders(id,number,user_id,order_time,amount,status,pay_status) "
                + "VALUES (98103,'AO3-ORDER-98103',7,NOW(),12.34,1,0)");
        doAnswer(invocation -> {
            notifications.add(invocation.getArgument(0, String.class));
            return null;
        }).when(notification).broadcast(anyString());
        logs.start();
        loggers.forEach(logger -> logger.addAppender(logs));
    }

    @AfterEach
    void tearDown() {
        loggers.forEach(logger -> logger.detachAppender(logs));
        logs.stop();
        MDC.clear();
    }

    @Test
    void paymentOutboxAndNotificationExposeOneSafeSearchableBusinessChain() throws Exception {
        MDC.put("token", SECRET_CANARY);
        assertThat(payment.handle(new PaymentCallbackCommand("AO3-ORDER-98103", "AO3-TXN-98103", 1234)))
                .isEqualTo(PaymentCallbackResult.APPLIED);
        MDC.clear();

        Map<String, Object> pending = jdbc.queryForMap("SELECT event_id,correlation_id,aggregate_id,"
                + "event_type,status,payload FROM outbox_event WHERE aggregate_id=98103");
        String eventId = (String) pending.get("event_id");
        assertThat(eventId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(pending).containsEntry("correlation_id", "AO3-TXN-98103")
                .containsEntry("aggregate_id", 98103L).containsEntry("event_type", "ORDER_PAID")
                .containsEntry("status", "PENDING");
        assertThat(jdbc.queryForMap("SELECT status,pay_status FROM orders WHERE id=98103"))
                .containsEntry("status", 2).containsEntry("pay_status", 1);
        JSONObject payload = JSON.parseObject((String) pending.get("payload"));
        assertThat(payload).containsEntry("orderId", 98103).containsEntry("orderNumber", "AO3-ORDER-98103")
                .containsEntry("userId", 7).containsEntry("amount", new java.math.BigDecimal("12.34"));
        payload.put("token", SECRET_CANARY);
        String wirePayload = payload.toJSONString();
        jdbc.update("UPDATE outbox_event SET payload=? WHERE event_id=?", wirePayload, eventId);

        OutboxPublisher publisher = new OutboxPublisher(events, rabbit, "ao3-trace-worker",
                Duration.ofSeconds(30), Duration.ofSeconds(2), Duration.ofMinutes(5),
                Duration.ofSeconds(5), 8, 512);
        assertThat(publisher.publishNext()).isTrue();
        awaitNotificationConsumption(eventId);
        assertThat(notifications).hasSize(1);
        assertThat(JSON.parseObject(notifications.peek()))
                .containsOnlyKeys("type", "orderId", "content")
                .containsEntry("type", 1).containsEntry("orderId", 98103)
                .containsEntry("content", "订单号：AO3-ORDER-98103");
        assertThat(jdbc.queryForMap("SELECT status,attempt_count,last_error FROM outbox_event WHERE event_id=?", eventId))
                .containsEntry("status", "SENT").containsEntry("attempt_count", 0).containsEntry("last_error", null);
        assertThat(jdbc.queryForMap("SELECT status,business_key FROM message_consumption "
                + "WHERE consumer_name='order-paid-notify' AND event_id=?", eventId))
                .containsEntry("status", "COMPLETED").containsEntry("business_key", "AO3-ORDER-98103");

        assertStage(PaymentCallbackService.class, "Verified payment persisted with pending outbox event", eventId);
        assertStage(OrderEventConsumer.class, "MQ 处理支付成功通知完成", eventId);
        assertStage(OutboxPublisher.class, "Outbox event published successfully", eventId);
        assertThat(logs.records).allSatisfy(record -> {
            assertThat(record.getFormattedMessage()).doesNotContain(SECRET_CANARY, wirePayload,
                    (String) pending.get("payload"), "\"userId\"", "\"amount\"");
            assertThat(record.getMDCPropertyMap()).doesNotContainKey("token")
                    .doesNotContainValue(SECRET_CANARY);
        });
    }

    private void awaitNotificationConsumption(String eventId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM message_consumption "
                    + "WHERE consumer_name='order-paid-notify' AND event_id=? AND status='COMPLETED'",
                    Integer.class, eventId) == 1) return;
            Thread.sleep(100);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_consumption "
                + "WHERE consumer_name='order-paid-notify' AND event_id=? AND status='COMPLETED'",
                Integer.class, eventId)).as("real Rabbit paid-notify consumer completed").isEqualTo(1);
    }

    private void assertStage(Class<?> logger, String meaning, String eventId) {
        List<ILoggingEvent> stage = logs.records.stream()
                .filter(record -> logger.getName().equals(record.getLoggerName())
                        && record.getFormattedMessage().startsWith(meaning))
                .collect(Collectors.toList());
        assertThat(stage).as("successful stage log from %s: %s", logger.getSimpleName(), meaning).hasSize(1);
        assertThat(stage.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(stage.get(0).getMDCPropertyMap()).containsEntry("eventId", eventId)
                .containsEntry("correlationId", "AO3-TXN-98103").containsEntry("orderId", "98103");
    }

    private static class CapturedLogs extends AppenderBase<ILoggingEvent> {
        private final ConcurrentLinkedQueue<ILoggingEvent> records = new ConcurrentLinkedQueue<>();

        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            records.add(event);
        }
    }
}
