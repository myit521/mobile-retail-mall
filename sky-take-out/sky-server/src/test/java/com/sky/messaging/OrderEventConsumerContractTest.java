package com.sky.messaging;

import com.sky.messaging.consumer.OrderEventEnvelope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderEventConsumerContractTest {

    @Test
    void rejectsUnsupportedEnvelopeVersionBeforeBusinessHandling() {
        assertThatThrownBy(() -> OrderEventEnvelope.fromJson("{\"eventId\":\"event-1\",\"correlationId\":\"correlation-1\","
                + "\"eventType\":\"ORDER_PAID\",\"payloadVersion\":2,\"aggregateId\":1,\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload version");
    }

    @Test
    void rejectsMissingStableEventIdentity() {
        assertThatThrownBy(() -> OrderEventEnvelope.fromJson("{\"correlationId\":\"correlation-1\","
                + "\"eventType\":\"ORDER_PAID\",\"payloadVersion\":1,\"aggregateId\":1,\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsControlCharactersInAsyncAndBusinessIdentifiers() {
        assertThatThrownBy(() -> OrderEventEnvelope.fromJson("{\"eventId\":\"event-1\\nFORGED\","
                + "\"correlationId\":\"correlation-1\",\"eventType\":\"ORDER_PAID\","
                + "\"payloadVersion\":1,\"aggregateId\":1,\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eventId");
        assertThatThrownBy(() -> OrderEventEnvelope.fromJson("{\"eventId\":\"event-1\","
                + "\"correlationId\":\"correlation-1\\rFORGED\",\"eventType\":\"ORDER_PAID\","
                + "\"payloadVersion\":1,\"aggregateId\":1,\"payload\":{}}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("correlationId");
    }
}
