package com.sky.messaging;

import com.sky.constant.RabbitMQConstant;
import com.sky.messaging.consumer.DeadLetterPublisher;
import com.sky.messaging.consumer.MessageConsumptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DeadLetterPublisherTest {

    @Test
    void failedPublishDoesNotRecordTerminalFailureSoRedeliveryCannotBecomeFalseDuplicate() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.doThrow(new AmqpException("broker-down"))
                .when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher(rabbit, consumption).publish(message(), new IllegalStateException("poison"), 3))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("broker-down");

        verify(consumption, never()).recordFailure(any(), any(), any(), any());
    }

    @Test
    void brokerAckAndNoReturnRecordsTerminalFailureAfterDurablePublish() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.when(consumption.sanitize(any())).thenReturn("safe-reason");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq(RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_EXCHANGE),
                eq(RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_ROUTING_KEY), any(Message.class), any(CorrelationData.class));

        publisher(rabbit, consumption).publish(message(), new IllegalStateException("poison"), 3);

        verify(consumption).recordFailure("order-paid-notify", "event-1", "42", "safe-reason");
    }

    @Test
    void returnedPublishDoesNotRecordTerminalFailure() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.when(consumption.sanitize(any())).thenReturn("safe-reason");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturnedMessage(new Message("returned".getBytes(StandardCharsets.UTF_8)));
            correlation.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher(rabbit, consumption).publish(message(), new IllegalStateException("poison"), 3))
                .isInstanceOf(AmqpException.class);

        verify(consumption, never()).recordFailure(any(), any(), any(), any());
    }

    @Test
    void overlengthRawEventIdPublishesOnceAndRecordsDeterministicBoundedIdentity() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.when(consumption.sanitize(any())).thenReturn("safe-reason");
        AtomicReference<Message> published = new AtomicReference<>();
        doAnswer(invocation -> {
            published.set(invocation.getArgument(2));
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
        String rawEventId = "e".repeat(65);

        publisher(rabbit, consumption).publish(message(rawEventId, "42"), new IllegalStateException("poison"), 3);

        ArgumentCaptor<String> persistedId = ArgumentCaptor.forClass(String.class);
        verify(consumption).recordFailure(eq("order-paid-notify"), persistedId.capture(), eq("42"), eq("safe-reason"));
        assertThat(persistedId.getValue()).startsWith("invalid:").hasSize(64);
        assertThat(String.valueOf((Object) published.get().getMessageProperties().getHeader("eventId")))
                .startsWith("invalid:").doesNotContain(rawEventId);
        verify(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void complexAndSensitiveEnvelopeValuesBecomeStableBoundedSupportedHeaders() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.when(consumption.sanitize(any())).thenReturn("safe-reason");
        List<Message> published = new ArrayList<>();
        doAnswer(invocation -> {
            published.add(invocation.getArgument(2));
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
        String secret = "secret-value-that-must-not-leak";
        String body = "{\"eventId\":{\"nested\":\"" + secret + "\"},"
                + "\"correlationId\":[\"" + secret + "\"],"
                + "\"eventType\":\"" + secret.repeat(20) + "\","
                + "\"payloadVersion\":{\"value\":1},"
                + "\"aggregateId\":{\"account\":\"" + secret + "\"},\"payload\":{}}";
        Message poison = queued(body);

        DeadLetterPublisher publisher = publisher(rabbit, consumption);
        publisher.publish(poison, new IllegalStateException("poison"), 3);
        publisher.publish(poison, new IllegalStateException("poison"), 3);

        assertThat(published).hasSize(2);
        for (String name : List.of("eventId", "correlationId", "eventType", "payloadVersion", "aggregateId")) {
            Object first = published.get(0).getMessageProperties().getHeader(name);
            Object second = published.get(1).getMessageProperties().getHeader(name);
            assertThat(first).isInstanceOf(String.class).isEqualTo(second);
            assertThat((String) first).startsWith("invalid:").hasSizeLessThanOrEqualTo(72).doesNotContain(secret);
        }
        verify(consumption, org.mockito.Mockito.times(2)).recordFailure(any(), any(), any(), eq("safe-reason"));
    }

    @Test
    void validEnvelopeIdentityHeadersRemainUnchanged() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.when(consumption.sanitize(any())).thenReturn("safe-reason");
        AtomicReference<Message> published = new AtomicReference<>();
        doAnswer(invocation -> {
            published.set(invocation.getArgument(2));
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));

        publisher(rabbit, consumption).publish(message(), new IllegalStateException("poison"), 3);

        Map<String, Object> headers = published.get().getMessageProperties().getHeaders();
        assertThat(headers.get("eventId")).isEqualTo("event-1");
        assertThat(headers.get("correlationId")).isEqualTo("corr-1");
        assertThat(headers.get("eventType")).isEqualTo("ORDER_PAID");
        assertThat(headers.get("payloadVersion")).isEqualTo(1);
        assertThat(headers.get("aggregateId")).isEqualTo(42);
    }

    @Test
    void missingEventIdAndOverlengthBusinessKeyUseBoundedStableFallbacks() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.when(consumption.sanitize(any())).thenReturn("safe-reason");
        ack(rabbit);
        Message poison = messageWithoutEventId("b".repeat(129));

        DeadLetterPublisher publisher = publisher(rabbit, consumption);
        publisher.publish(poison, new IllegalStateException("poison"), 3);
        publisher.publish(poison, new IllegalStateException("poison"), 3);

        ArgumentCaptor<String> eventIds = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> businessKeys = ArgumentCaptor.forClass(String.class);
        verify(consumption, org.mockito.Mockito.times(2)).recordFailure(eq("order-paid-notify"),
                eventIds.capture(), businessKeys.capture(), eq("safe-reason"));
        assertThat(eventIds.getAllValues()).hasSize(2).allMatch(value -> value.length() <= 64)
                .containsOnly(eventIds.getValue());
        assertThat(businessKeys.getAllValues()).hasSize(2).allMatch(value -> value.length() <= 128)
                .containsOnly(businessKeys.getValue());
    }

    @Test
    void brokerNackDoesNotRecordTerminalFailure() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.when(consumption.sanitize(any())).thenReturn("safe-reason");
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().set(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher(rabbit, consumption).publish(message(), new IllegalStateException("poison"), 3))
                .isInstanceOf(AmqpException.class);
        verify(consumption, never()).recordFailure(any(), any(), any(), any());
    }

    @Test
    void confirmationTimeoutDoesNotRecordTerminalFailure() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        org.mockito.Mockito.when(consumption.sanitize(any())).thenReturn("safe-reason");

        assertThatThrownBy(() -> new DeadLetterPublisher(rabbit, consumption, Duration.ZERO)
                .publish(message(), new IllegalStateException("poison"), 3))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("confirmation failed");
        verify(consumption, never()).recordFailure(any(), any(), any(), any());
    }

    private DeadLetterPublisher publisher(RabbitTemplate rabbit, MessageConsumptionService consumption) {
        return new DeadLetterPublisher(rabbit, consumption, Duration.ofSeconds(1));
    }

    private Message message() {
        return message("event-1", "42");
    }

    private Message message(String eventId, String aggregateId) {
        String body = "{\"eventId\":\"" + eventId + "\",\"correlationId\":\"corr-1\",\"eventType\":\"ORDER_PAID\","
                + "\"payloadVersion\":1,\"aggregateId\":" + aggregateId + ",\"payload\":{}}";
        return queued(body);
    }

    private Message messageWithoutEventId(String aggregateId) {
        String body = "{\"correlationId\":\"corr-1\",\"eventType\":\"ORDER_PAID\","
                + "\"payloadVersion\":1,\"aggregateId\":42,\"payload\":{}}";
        body = body.replace("\"aggregateId\":42", "\"aggregateId\":\"" + aggregateId + "\"");
        return queued(body);
    }

    private Message queued(String body) {
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).build();
        message.getMessageProperties().setConsumerQueue(RabbitMQConstant.ORDER_PAID_NOTIFY_QUEUE);
        return message;
    }

    private void ack(RabbitTemplate rabbit) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(), any(), any(Message.class), any(CorrelationData.class));
    }
}
