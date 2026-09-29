package com.sky.messaging.outbox;

import com.alibaba.fastjson.JSON;
import com.sky.constant.RabbitMQConstant;
import com.sky.entity.OutboxEvent;
import com.sky.observability.MdcContext;
import com.sky.observability.BusinessMetrics;
import com.sky.observability.SensitiveValueSanitizer;
import com.sky.observability.SafeLogIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "sky.outbox", name = "enabled", havingValue = "true")
@Slf4j
public class OutboxPublisher {
    private final OutboxEventMapper events;
    private final RabbitTemplate rabbit;
    private final String workerId;
    private final Duration leaseDuration;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Duration confirmTimeout;
    private final int maxAttempts;
    private final int errorLength;
    private final Clock clock;
    @Autowired(required = false)
    private BusinessMetrics metrics;

    @Autowired
    public OutboxPublisher(OutboxEventMapper events, RabbitTemplate rabbit,
                           @Value("${sky.outbox.worker-id:${random.uuid}}") String workerId,
                           @Value("${sky.outbox.lease-duration:PT30S}") Duration leaseDuration,
                           @Value("${sky.outbox.initial-backoff:PT2S}") Duration initialBackoff,
                           @Value("${sky.outbox.max-backoff:PT5M}") Duration maxBackoff,
                           @Value("${sky.outbox.confirm-timeout:PT5S}") Duration confirmTimeout,
                           @Value("${sky.outbox.max-attempts:8}") int maxAttempts,
                           @Value("${sky.outbox.error-length:512}") int errorLength) {
        this(events, rabbit, workerId, leaseDuration, initialBackoff, maxBackoff,
                confirmTimeout, maxAttempts, errorLength, Clock.systemDefaultZone());
    }

    public OutboxPublisher(OutboxEventMapper events, RabbitTemplate rabbit, String workerId,
                           Duration leaseDuration, Duration initialBackoff, Duration maxBackoff,
                           Duration confirmTimeout, int maxAttempts, int errorLength, Clock clock) {
        this.events = events;
        this.rabbit = rabbit;
        this.workerId = workerId;
        this.leaseDuration = leaseDuration;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.confirmTimeout = confirmTimeout;
        this.maxAttempts = maxAttempts;
        this.errorLength = errorLength;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sky.outbox.poll-delay-ms:1000}")
    public void publishDue() {
        while (publishNext()) {
            // Drain all currently due rows; each claim is its own short transaction.
        }
    }

    public boolean publishNext() {
        OutboxEvent event = claim();
        if (event == null) {
            return false;
        }
        try (MdcContext ignored = MdcContext.open(Map.of(
                "eventId", event.getEventId(),
                "correlationId", event.getCorrelationId(),
                "orderId", event.getAggregateId()))) {
            try {
                CorrelationData correlation = new CorrelationData(event.getEventId());
                Message message = MessageBuilder.withBody(envelope(event))
                    .setContentType("application/json")
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(event.getEventId())
                    .setCorrelationId(event.getCorrelationId())
                    .setHeader("eventId", event.getEventId())
                    .setHeader("eventType", event.getEventType())
                    .setHeader("payloadVersion", event.getPayloadVersion())
                    .setHeader("aggregateId", event.getAggregateId())
                    .build();
                rabbit.send(RabbitMQConstant.ORDER_EVENT_EXCHANGE, route(event), message, correlation);
                CorrelationData.Confirm confirm = correlation.getFuture()
                        .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (correlation.getReturnedMessage() != null || correlation.getReturned() != null) {
                    if (metrics != null) metrics.outboxPublishFailure("returned");
                    recordFailure(event, "message-returned");
                } else if (!confirm.isAck()) {
                    if (metrics != null) metrics.outboxPublishFailure("nack");
                    recordFailure(event, confirm.getReason() == null ? "broker-nack" : confirm.getReason());
                } else if (events.markSent(event.getEventId(), event.getLeaseOwner()) != 1) {
                    log.warn("Outbox ACK could not update leased event, eventId={}",
                            SafeLogIdentifier.forLog(event.getEventId(), 64));
                } else {
                    log.info("Outbox event published successfully");
                }
            } catch (Exception exception) {
                if (metrics != null) metrics.outboxPublishFailure("exception");
                recordFailure(event, exception.getClass().getSimpleName() + ":" + safeMessage(exception));
            }
        }
        return true;
    }

    public OutboxEvent claim() {
        LocalDateTime now = LocalDateTime.now(clock);
        return events.claimNext(workerId + "-" + UUID.randomUUID(), now, now.plus(leaseDuration));
    }

    private void recordFailure(OutboxEvent event, String error) {
        int attempts = event.getAttemptCount() + 1;
        String boundedError = abbreviate(error);
        if (attempts >= maxAttempts) {
            int affected = events.markFailed(event.getEventId(), event.getLeaseOwner(), attempts, boundedError);
            warnLostLease(event, "FAILED", attempts, affected);
            return;
        }
        long multiplier = 1L << Math.min(attempts - 1, 30);
        Duration delay = initialBackoff.multipliedBy(multiplier);
        if (delay.compareTo(maxBackoff) > 0) {
            delay = maxBackoff;
        }
        int affected = events.markRetry(event.getEventId(), event.getLeaseOwner(), attempts,
                LocalDateTime.now(clock).plus(delay), boundedError);
        warnLostLease(event, "PENDING", attempts, affected);
    }

    private void warnLostLease(OutboxEvent event, String targetStatus, int attempts, int affected) {
        if (affected != 1) {
            log.warn("Outbox failure state update lost lease, eventId={}, leaseOwner={}, targetStatus={}, attempts={}, affected={}",
                    SafeLogIdentifier.forLog(event.getEventId(), 64),
                    SafeLogIdentifier.forLog(event.getLeaseOwner(), 128), targetStatus, attempts, affected);
        }
    }

    private String route(OutboxEvent event) {
        if ("ORDER_PAID".equals(event.getEventType())) {
            return RabbitMQConstant.ORDER_PAID_ROUTING_KEY;
        }
        throw new IllegalArgumentException("Unsupported outbox event type");
    }

    private byte[] envelope(OutboxEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.getEventId());
        envelope.put("correlationId", event.getCorrelationId());
        envelope.put("eventType", event.getEventType());
        envelope.put("payloadVersion", event.getPayloadVersion());
        envelope.put("aggregateId", event.getAggregateId());
        envelope.put("payload", JSON.parse(event.getPayload()));
        return JSON.toJSONString(envelope).getBytes(StandardCharsets.UTF_8);
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? "no-message" : SensitiveValueSanitizer.sanitize(exception.getMessage());
    }

    private String abbreviate(String value) {
        return value.length() <= errorLength ? value : value.substring(0, errorLength);
    }
}
