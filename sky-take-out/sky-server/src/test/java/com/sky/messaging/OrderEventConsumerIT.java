package com.sky.messaging;

import com.alibaba.fastjson.JSON;
import com.sky.constant.RabbitMQConstant;
import com.sky.notification.api.OrderNotificationPort;
import com.sky.notification.internal.messaging.OrderEventConsumer;
import com.sky.support.IntegrationTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {"sky.outbox.enabled=false", "sky.messaging.consumer.max-attempts=3"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderEventConsumerIT extends IntegrationTestBase {

    @Autowired private RabbitTemplate rabbit;
    @Autowired private RabbitAdmin admin;
    @Autowired private MeterRegistry meterRegistry;
    private JdbcTemplate jdbc;
    @MockBean private OrderNotificationPort notification;
    @SpyBean private OrderEventConsumer consumer;
    @MockBean private ServerEndpointExporter serverEndpointExporter;

    @Autowired
    void dataSource(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM message_consumption");
        jdbc.update("DELETE FROM order_detail");
        jdbc.update("DELETE FROM orders");
        admin.purgeQueue(RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_QUEUE, true);
        admin.purgeQueue(RabbitMQConstant.ORDER_PAID_NOTIFY_QUEUE, true);
        admin.purgeQueue(RabbitMQConstant.ORDER_TIMEOUT_PROCESS_QUEUE, true);
    }

    @Test
    void duplicateEventExecutesNotificationOnceAndIsAcknowledged() throws Exception {
        String eventId = UUID.randomUUID().toString();
        byte[] body = paidEnvelope(eventId, 101L, "ORDER-101");

        rabbit.convertAndSend(RabbitMQConstant.ORDER_EVENT_EXCHANGE, RabbitMQConstant.ORDER_PAID_ROUTING_KEY, body);
        rabbit.convertAndSend(RabbitMQConstant.ORDER_EVENT_EXCHANGE, RabbitMQConstant.ORDER_PAID_ROUTING_KEY, body);

        verify(notification, timeout(10000).times(1)).broadcast(org.mockito.ArgumentMatchers.anyString());
        await(() -> Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message_consumption WHERE consumer_name='order-paid-notify' AND event_id=? AND status='COMPLETED'",
                Integer.class, eventId)));
    }

    @Test
    void poisonEnvelopeIsDeadLetteredAfterExactlyConfiguredAttemptsWithSafeIdentityHeaders() throws Exception {
        double deadLettersBefore = meterRegistry.counter("sky.messaging.dead.letters",
                "consumer", "order_paid_notify").count();
        String eventId = UUID.randomUUID().toString();
        byte[] invalid = paidEnvelope(eventId, 102L, "ORDER-102").clone();
        String text = new String(invalid, StandardCharsets.UTF_8).replace("\"payloadVersion\":1", "\"payloadVersion\":99");

        rabbit.convertAndSend(RabbitMQConstant.ORDER_EVENT_EXCHANGE, RabbitMQConstant.ORDER_PAID_ROUTING_KEY,
                MessageBuilder.withBody(text.getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setHeader("Authorization", "Bearer sensitive-test-value")
                        .setHeader("token", "sensitive-token-value")
                        .setHeader("internalDebug", "unapproved-custom-value")
                        .build());

        Message dead = receive(RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_QUEUE, 10000);
        assertThat(dead).isNotNull();
        assertThat(dead.getBody()).containsExactly(text.getBytes(StandardCharsets.UTF_8));
        assertThat(dead.getMessageProperties().getHeaders().keySet().stream()
                .filter(header -> !header.startsWith("spring_")).toArray(String[]::new)).containsExactlyInAnyOrder(
                "eventId", "correlationId", "aggregateId", "payloadVersion", "eventType",
                "failureReason", "consumerName", "deliveryAttempts");
        assertThat(dead.getMessageProperties().getHeaders().keySet())
                .doesNotContain("Authorization", "token", "internalDebug");
        assertThat(dead).isNotNull();
        assertThat((Object) dead.getMessageProperties().getHeader("eventId")).isEqualTo(eventId);
        assertThat((Object) dead.getMessageProperties().getHeader("correlationId")).isEqualTo("corr-" + eventId);
        assertThat(((Number) dead.getMessageProperties().getHeader("aggregateId")).longValue()).isEqualTo(102L);
        assertThat((Object) dead.getMessageProperties().getHeader("consumerName")).isIn("order-paid-notify", "order-paid-audit");
        assertThat((Object) dead.getMessageProperties().getHeader("payloadVersion")).isEqualTo(99);
        assertThat((Object) dead.getMessageProperties().getHeader("eventType")).isEqualTo("ORDER_PAID");
        assertThat((Object) dead.getMessageProperties().getHeader("deliveryAttempts")).isEqualTo(3);
        assertThat(String.valueOf(dead.getMessageProperties().<Object>getHeader("failureReason")))
                .doesNotContainIgnoringCase("password", "token", "ciphertext");
        verify(consumer, timeout(10000).times(3)).handleOrderPaidNotify(org.mockito.ArgumentMatchers.any());
        await(() -> meterRegistry.counter("sky.messaging.dead.letters",
                "consumer", "order_paid_notify").count() - deadLettersBefore == 1.0d);
    }

    @Test
    void timeoutAfterPaymentDoesNotCancelPaidOrder() throws Exception {
        jdbc.update("INSERT INTO orders(id,number,status,user_id,order_time,pay_method,pay_status,amount) VALUES (?,?,?,?,?,?,?,?)",
                103L, "ORDER-103", 2, 7L, LocalDateTime.now(), 1, 1, new BigDecimal("18.80"));
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNumber", "ORDER-103");
        rabbit.convertAndSend(RabbitMQConstant.ORDER_EVENT_EXCHANGE,
                RabbitMQConstant.ORDER_TIMEOUT_PROCESS_ROUTING_KEY,
                JSON.toJSONBytes(envelope(eventId, "ORDER_TIMEOUT", 103L, payload)));

        await(() -> Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message_consumption WHERE consumer_name='order-timeout' AND event_id=? AND status='COMPLETED'",
                Integer.class, eventId)));
        Map<String, Object> order = jdbc.queryForMap("SELECT status,pay_status FROM orders WHERE id=103");
        assertThat(((Number) order.get("status")).intValue()).isEqualTo(2);
        assertThat(((Number) order.get("pay_status")).intValue()).isEqualTo(1);
    }

    private byte[] paidEnvelope(String eventId, long orderId, String orderNumber) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNumber", orderNumber);
        payload.put("userId", 7L);
        payload.put("amount", new BigDecimal("18.80"));
        return JSON.toJSONBytes(envelope(eventId, "ORDER_PAID", orderId, payload));
    }

    private Map<String, Object> envelope(String eventId, String type, long aggregateId, Map<String, Object> payload) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("eventId", eventId);
        value.put("correlationId", "corr-" + eventId);
        value.put("eventType", type);
        value.put("payloadVersion", 1);
        value.put("aggregateId", aggregateId);
        value.put("payload", payload);
        return value;
    }

    private Message receive(String queue, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Message message;
        do {
            message = rabbit.receive(queue);
            if (message != null) return message;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        return null;
    }

    private void await(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (check.done()) return;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        assertThat(check.done()).isTrue();
    }

    private interface Check { boolean done(); }
}
