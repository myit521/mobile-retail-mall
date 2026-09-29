package com.sky.notification.internal.messaging;

import com.sky.message.OrderPaidMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderEventMessageAdapterTest {

    @Test
    void adaptsCurrentJsonEnvelope() {
        String json = "{\"eventId\":\"event-1\",\"correlationId\":\"corr-1\",\"eventType\":\"ORDER_PAID\","
                + "\"payloadVersion\":1,\"aggregateId\":41,\"payload\":{\"orderId\":41,"
                + "\"orderNumber\":\"ORDER-41\",\"userId\":7,\"amount\":\"18.80\"}}";

        OrderEventMessageAdapter.AdaptedOrderEvent<com.sky.order.api.event.OrderPaidMessage> adapted =
                OrderEventMessageAdapter.toPaid(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(adapted.getEnvelope().getEventId()).isEqualTo("event-1");
        assertThat(adapted.getPayload().getOrderNumber()).isEqualTo("ORDER-41");
        assertThat(adapted.getPayload().getAmount()).isEqualByComparingTo("18.80");
    }

    @Test
    void preservesLegacyDurableJavaMessageCompatibilityWithStableSyntheticIdentity() {
        OrderPaidMessage legacy = new OrderPaidMessage(42L, "ORDER-42", 7L, new BigDecimal("9.90"));

        OrderEventMessageAdapter.AdaptedOrderEvent<com.sky.order.api.event.OrderPaidMessage> first =
                OrderEventMessageAdapter.toPaid(legacy);
        OrderEventMessageAdapter.AdaptedOrderEvent<com.sky.order.api.event.OrderPaidMessage> duplicate =
                OrderEventMessageAdapter.toPaid(legacy);

        assertThat(first.getPayload().getOrderNumber()).isEqualTo("ORDER-42");
        assertThat(first.getEnvelope().getEventId()).isEqualTo(duplicate.getEnvelope().getEventId());
    }

    @Test
    void rejectsPaidPayloadWhoseOrderDoesNotMatchEnvelopeAggregate() {
        String json = "{\"eventId\":\"event-2\",\"correlationId\":\"corr-2\",\"eventType\":\"ORDER_PAID\","
                + "\"payloadVersion\":1,\"aggregateId\":41,\"payload\":{\"orderId\":42,"
                + "\"orderNumber\":\"ORDER-42\",\"userId\":7,\"amount\":\"18.80\"}}";

        assertThatThrownBy(() -> OrderEventMessageAdapter.toPaid(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(org.springframework.amqp.support.converter.MessageConversionException.class)
                .hasMessageContaining("aggregateId");
    }
}
