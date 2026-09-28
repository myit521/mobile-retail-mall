package com.sky.messaging;

import com.sky.entity.OutboxEvent;
import com.sky.messaging.outbox.OutboxEventMapper;
import com.sky.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    @Test
    void ackMarksClaimedEventSentUsingEventIdAsCorrelation() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        OutboxEvent event = pendingEvent(0);
        when(mapper.claimNext(any(), any(), any())).thenReturn(event);
        when(mapper.markSent(any(), any())).thenReturn(1);
        AtomicReference<CorrelationData> correlation = new AtomicReference<>();
        AtomicReference<Message> published = new AtomicReference<>();
        MDC.put("traceId", "request-trace");
        doAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isEqualTo("request-trace");
            assertThat(MDC.get("eventId")).isEqualTo("event-1");
            assertThat(MDC.get("correlationId")).isEqualTo("correlation-1");
            assertThat(MDC.get("orderId")).isEqualTo("42");
            assertThat(MDC.get("payload")).isNull();
            published.set(invocation.getArgument(2));
            CorrelationData data = invocation.getArgument(3);
            correlation.set(data);
            data.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("order.event.exchange"), eq("order.paid"), any(Message.class), any(CorrelationData.class));

        OutboxPublisher publisher = publisher(mapper, rabbit, 3);
        assertThat(publisher.publishNext()).isTrue();
        assertThat(MDC.get("traceId")).isEqualTo("request-trace");
        MDC.clear();

        assertThat(correlation.get().getId()).isEqualTo("event-1");
        assertThat(new String(published.get().getBody(), StandardCharsets.UTF_8))
                .contains("\"eventId\":\"event-1\"")
                .contains("\"correlationId\":\"correlation-1\"")
                .contains("\"eventType\":\"ORDER_PAID\"")
                .contains("\"payloadVersion\":1")
                .contains("\"aggregateId\":42")
                .contains("\"payload\":{\"orderId\":42}");
        verify(mapper).markSent("event-1", event.getLeaseOwner());
        verify(mapper, never()).markRetry(any(), any(), anyInt(), any(), any());
    }

    @Test
    void nackPersistsBoundedBackoffAndDoesNotMarkSent() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        OutboxEvent event = pendingEvent(1);
        when(mapper.claimNext(any(), any(), any())).thenReturn(event);
        when(mapper.markRetry(any(), any(), anyInt(), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.getFuture().set(new CorrelationData.Confirm(false, "broker-nack"));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));

        OutboxPublisher publisher = publisher(mapper, rabbit, 3);
        assertThat(publisher.publishNext()).isTrue();

        verify(mapper).markRetry(eq("event-1"), eq(event.getLeaseOwner()), eq(2),
                eq(LocalDateTime.of(2026, 9, 3, 10, 1, 4)), eq("broker-nack"));
        verify(mapper, never()).markSent(any(), any());
    }

    @Test
    void returnedMessageIsFailureEvenWhenBrokerAcks() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        OutboxEvent event = pendingEvent(0);
        when(mapper.claimNext(any(), any(), any())).thenReturn(event);
        when(mapper.markRetry(any(), any(), anyInt(), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.setReturnedMessage(new Message("returned".getBytes(StandardCharsets.UTF_8)));
            data.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));

        publisher(mapper, rabbit, 3).publishNext();

        verify(mapper).markRetry(eq("event-1"), eq(event.getLeaseOwner()), eq(1), any(), eq("message-returned"));
        verify(mapper, never()).markSent(any(), any());
    }

    @Test
    void finalFailureBecomesQueryableInsteadOfRetryingForever() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        OutboxEvent event = pendingEvent(2);
        when(mapper.claimNext(any(), any(), any())).thenReturn(event);
        when(mapper.markFailed(any(), any(), anyInt(), any())).thenReturn(1);
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.getFuture().set(new CorrelationData.Confirm(false, "still-down"));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));

        publisher(mapper, rabbit, 3).publishNext();

        verify(mapper).markFailed("event-1", event.getLeaseOwner(), 3, "still-down");
        verify(mapper, never()).markRetry(any(), any(), anyInt(), any(), any());
    }

    @Test
    void brokerExceptionPersistsRetryInsteadOfLosingTheClaim() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        OutboxEvent event = pendingEvent(0);
        when(mapper.claimNext(any(), any(), any())).thenReturn(event);
        when(mapper.markRetry(any(), any(), anyInt(), any(), any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpConnectException(
                        new java.net.ConnectException("broker-down")))
                .when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));

        publisher(mapper, rabbit, 3).publishNext();

        verify(mapper).markRetry(eq("event-1"), eq(event.getLeaseOwner()), eq(1), any(),
                org.mockito.ArgumentMatchers.startsWith("AmqpConnectException:"));
        verify(mapper, never()).markSent(any(), any());
    }

    @Test
    void confirmTimeoutSchedulesRetryFromFailureTime() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(mapper.claimNext(any(), any(), any())).thenReturn(pendingEvent(0));
        when(mapper.markRetry(any(), any(), anyInt(), any(), any())).thenReturn(1);
        Clock failureClock = Clock.fixed(Instant.parse("2026-09-03T10:01:00Z"), ZoneOffset.UTC);

        publisher(mapper, rabbit, 3, failureClock, Duration.ZERO).publishNext();

        verify(mapper).markRetry(eq("event-1"), eq("worker-a"), eq(1),
                eq(LocalDateTime.of(2026, 9, 3, 10, 1, 2)),
                org.mockito.ArgumentMatchers.startsWith("TimeoutException:"));
    }

    @Test
    void lostLeaseWhileRecordingFailureEmitsStructuredWarning() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(mapper.claimNext(any(), any(), any())).thenReturn(pendingEvent(0));
        when(mapper.markRetry(any(), any(), anyInt(), any(), any())).thenReturn(0);
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.getFuture().set(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(OutboxPublisher.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            publisher(mapper, rabbit, 3).publishNext();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
                .contains("eventId=event-1").contains("leaseOwner=worker-a")
                .contains("targetStatus=PENDING").contains("attempts=1"));
    }

    private static OutboxPublisher publisher(OutboxEventMapper mapper, RabbitTemplate rabbit, int maxAttempts) {
        return publisher(mapper, rabbit, maxAttempts,
                Clock.fixed(Instant.parse("2026-09-03T10:01:00Z"), ZoneOffset.UTC), Duration.ofSeconds(1));
    }

    private static OutboxPublisher publisher(OutboxEventMapper mapper, RabbitTemplate rabbit, int maxAttempts,
                                             Clock clock, Duration confirmTimeout) {
        return new OutboxPublisher(mapper, rabbit, "worker-a", Duration.ofSeconds(30),
                Duration.ofSeconds(2), Duration.ofSeconds(8), confirmTimeout, maxAttempts, 512, clock);
    }

    private static OutboxEvent pendingEvent(int attempts) {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 9, 3, 10, 0);
        return OutboxEvent.builder()
                .eventId("event-1")
                .correlationId("correlation-1")
                .eventType("ORDER_PAID")
                .payloadVersion(1)
                .aggregateId(42L)
                .payload("{\"orderId\":42}")
                .attemptCount(attempts)
                .leaseOwner("worker-a")
                .claimedAt(claimedAt)
                .build();
    }
}
