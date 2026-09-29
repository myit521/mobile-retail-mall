package com.sky.messaging.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.RabbitMQConstant;
import com.sky.observability.BusinessMetrics;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Component
public class DeadLetterPublisher {
    private final RabbitTemplate rabbit;
    private final MessageConsumptionService consumption;
    private final Duration confirmTimeout;
    @Autowired(required = false)
    private BusinessMetrics metrics;

    public DeadLetterPublisher(RabbitTemplate rabbit, MessageConsumptionService consumption,
                               @org.springframework.beans.factory.annotation.Value(
                                       "${sky.messaging.consumer.dlq-confirm-timeout:PT5S}") Duration confirmTimeout) {
        this.rabbit = rabbit;
        this.consumption = consumption;
        this.confirmTimeout = confirmTimeout;
    }

    public void publish(Message original, Throwable cause, int attempts) {
        JSONObject raw = parse(original);
        String consumerName = consumerName(original.getMessageProperties().getConsumerQueue());
        String rawEventId = value(raw, "eventId", header(original, "eventId",
                original.getMessageProperties().getMessageId()));
        String rawBusinessKey = value(raw, "aggregateId", "legacy-unresolved");
        String eventId = persistenceEventId(rawEventId, original.getBody());
        String businessKey = persistenceBusinessKey(rawBusinessKey);
        String reason = consumption.sanitize(cause.getClass().getSimpleName() + ":" + cause.getMessage());
        MessageBuilderSupport<Message> builder = MessageBuilder.withBody(original.getBody())
                .setHeader("failureReason", reason)
                .setHeader("consumerName", consumerName)
                .setHeader("deliveryAttempts", attempts);
        copyEnvelopeHeaders(builder, raw, original);
        CorrelationData correlation = new CorrelationData("dlq-" + eventId);
        try {
            rabbit.send(RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_EXCHANGE,
                    RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_ROUTING_KEY, builder.build(), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturnedMessage() != null || correlation.getReturned() != null) {
                throw new AmqpException("DLQ publish was not confirmed");
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AmqpException("DLQ publish confirmation failed", exception);
        }
        // Publish first. If this database write fails, throw and allow redelivery; this can create a
        // duplicate DLQ copy, which is preferable to ACKing an original without durable evidence.
        consumption.recordFailure(consumerName, eventId, businessKey, reason);
        if (metrics != null) metrics.deadLetter(metricConsumer(consumerName));
    }

    private BusinessMetrics.Consumer metricConsumer(String consumerName) {
        if ("order-timeout".equals(consumerName)) return BusinessMetrics.Consumer.ORDER_TIMEOUT;
        if ("order-paid-notify".equals(consumerName)) return BusinessMetrics.Consumer.ORDER_PAID_NOTIFY;
        if ("order-paid-audit".equals(consumerName)) return BusinessMetrics.Consumer.ORDER_PAID_AUDIT;
        return BusinessMetrics.Consumer.UNKNOWN;
    }

    private JSONObject parse(Message message) {
        try {
            return JSON.parseObject(new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void copyEnvelopeHeaders(MessageBuilderSupport<Message> builder, JSONObject envelope, Message original) {
        for (String name : new String[]{"eventId", "correlationId", "aggregateId", "payloadVersion", "eventType"}) {
            boolean inEnvelope = envelope != null && envelope.containsKey(name);
            boolean inOriginalHeaders = original.getMessageProperties().getHeaders().containsKey(name);
            if (inEnvelope || inOriginalHeaders) {
                Object value = inEnvelope ? envelope.get(name)
                        : original.getMessageProperties().getHeaders().get(name);
                builder.setHeader(name, safeHeader(name, value));
            }
        }
    }

    private String persistenceEventId(String rawEventId, byte[] body) {
        if (isBounded(rawEventId, 64)) return rawEventId;
        MessageDigest digest = sha256();
        digest.update("poison-event-id\0".getBytes(StandardCharsets.UTF_8));
        if (rawEventId != null) digest.update(rawEventId.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(body);
        return "invalid:" + HexFormat.of().formatHex(digest.digest()).substring(0, 56);
    }

    private String persistenceBusinessKey(String rawBusinessKey) {
        if (isBounded(rawBusinessKey, 128)) return rawBusinessKey;
        MessageDigest digest = sha256();
        digest.update("poison-business-key\0".getBytes(StandardCharsets.UTF_8));
        if (rawBusinessKey != null) digest.update(rawBusinessKey.getBytes(StandardCharsets.UTF_8));
        return "invalid:" + HexFormat.of().formatHex(digest.digest());
    }

    private boolean isBounded(String value, int maxLength) {
        return value != null && !value.trim().isEmpty() && value.length() <= maxLength;
    }

    private Object safeHeader(String name, Object value) {
        if (("eventId".equals(name) || "correlationId".equals(name) || "eventType".equals(name))
                && value instanceof String && safeIdentityText((String) value)) {
            return value;
        }
        if ("aggregateId".equals(name) && (value instanceof Integer || value instanceof Long)) {
            return value;
        }
        if ("payloadVersion".equals(name) && value instanceof Integer) {
            return value;
        }
        return digestRepresentation("dlq-header-" + name, JSON.toJSONString(value));
    }

    private boolean safeIdentityText(String value) {
        return value.length() >= 1 && value.length() <= 64
                && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*");
    }

    private String digestRepresentation(String domain, String value) {
        MessageDigest digest = sha256();
        digest.update(domain.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return "invalid:" + HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String consumerName(String queue) {
        if (RabbitMQConstant.ORDER_TIMEOUT_PROCESS_QUEUE.equals(queue)) return "order-timeout";
        if (RabbitMQConstant.ORDER_PAID_NOTIFY_QUEUE.equals(queue)) return "order-paid-notify";
        if (RabbitMQConstant.ORDER_PAID_AUDIT_QUEUE.equals(queue)) return "order-paid-audit";
        return "unknown-order-consumer";
    }

    private String header(Message message, String name, String fallback) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        return value == null ? fallback : String.valueOf(value);
    }

    private String value(JSONObject envelope, String name, String fallback) {
        Object value = envelope == null ? null : envelope.get(name);
        return value == null ? fallback : String.valueOf(value);
    }
}
