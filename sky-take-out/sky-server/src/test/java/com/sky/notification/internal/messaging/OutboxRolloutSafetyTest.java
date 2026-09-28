package com.sky.notification.internal.messaging;

import com.sky.entity.OutboxEvent;
import com.sky.messaging.outbox.OutboxEventMapper;
import com.sky.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxRolloutSafetyTest {

    @Test
    void publisherRemainsExplicitlyPropertyGatedAfterEnvelopeConsumerInstallation() {
        ConditionalOnProperty condition = AnnotationUtils.findAnnotation(OutboxPublisher.class, ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("sky.outbox");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test
    void exactPublishedEnvelopeIsAcceptedByCurrentConsumerAdapter() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        OutboxEvent event = OutboxEvent.builder().eventId("event-1").correlationId("correlation-1")
                .eventType("ORDER_PAID").payloadVersion(1).aggregateId(42L)
                .payload("{\"orderId\":42}").attemptCount(0).leaseOwner("worker-a")
                .claimedAt(LocalDateTime.of(2026, 9, 3, 10, 0)).build();
        when(mapper.claimNext(any(), any(), any())).thenReturn(event);
        when(mapper.markSent(any(), any())).thenReturn(1);
        AtomicReference<Message> published = new AtomicReference<>();
        doAnswer(invocation -> {
            published.set(invocation.getArgument(2));
            CorrelationData data = invocation.getArgument(3);
            data.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
        OutboxPublisher publisher = new OutboxPublisher(mapper, rabbit, "worker-a", Duration.ofSeconds(30),
                Duration.ofSeconds(2), Duration.ofSeconds(8), Duration.ofSeconds(1), 3, 512,
                Clock.systemUTC());

        assertThat(publisher.publishNext()).isTrue();
        OrderEventMessageAdapter.AdaptedOrderEvent<com.sky.order.api.event.OrderPaidMessage> adapted =
                OrderEventMessageAdapter.toPaid(published.get().getBody());
        assertThat(adapted.getEnvelope().getEventId()).isEqualTo("event-1");
        assertThat(adapted.getEnvelope().getCorrelationId()).isEqualTo("correlation-1");
        assertThat(adapted.getEnvelope().getPayloadVersion()).isEqualTo(1);
        assertThat(adapted.getPayload().getOrderId()).isEqualTo(42L);
    }
}
