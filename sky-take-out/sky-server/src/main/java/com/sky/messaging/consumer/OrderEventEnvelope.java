package com.sky.messaging.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.observability.SafeLogIdentifier;

import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class OrderEventEnvelope {
    public static final int CURRENT_PAYLOAD_VERSION = 1;

    private final String eventId;
    private final String correlationId;
    private final String eventType;
    private final int payloadVersion;
    private final Long aggregateId;
    private final JSONObject payload;

    private OrderEventEnvelope(String eventId, String correlationId, String eventType,
                               int payloadVersion, Long aggregateId, JSONObject payload) {
        this.eventId = SafeLogIdentifier.require(eventId, "eventId", 64);
        this.correlationId = SafeLogIdentifier.require(correlationId, "correlationId", 64);
        this.eventType = SafeLogIdentifier.require(eventType, "eventType", 64);
        if (payloadVersion != CURRENT_PAYLOAD_VERSION) {
            throw new IllegalArgumentException("Unsupported payload version: " + payloadVersion);
        }
        this.payloadVersion = payloadVersion;
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        this.payload = Objects.requireNonNull(payload, "payload is required");
    }

    public static OrderEventEnvelope fromJson(String json) {
        JSONObject value;
        try {
            value = JSON.parseObject(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid order event envelope", exception);
        }
        if (value == null) {
            throw new IllegalArgumentException("Invalid order event envelope");
        }
        Integer version = value.getInteger("payloadVersion");
        return new OrderEventEnvelope(value.getString("eventId"), value.getString("correlationId"),
                value.getString("eventType"), version == null ? -1 : version,
                value.getLong("aggregateId"), value.getJSONObject("payload"));
    }

    public static OrderEventEnvelope legacy(String eventType, String businessKey, Long aggregateId, JSONObject payload) {
        String eventId = UUID.nameUUIDFromBytes((eventType + ":" + businessKey).getBytes(StandardCharsets.UTF_8)).toString();
        return new OrderEventEnvelope(eventId, eventId, eventType, CURRENT_PAYLOAD_VERSION, aggregateId, payload);
    }

    public void requireType(String expected) {
        if (!expected.equals(eventType)) {
            throw new IllegalArgumentException("Unexpected event type: " + eventType);
        }
    }

    public String getEventId() { return eventId; }
    public String getCorrelationId() { return correlationId; }
    public String getEventType() { return eventType; }
    public int getPayloadVersion() { return payloadVersion; }
    public Long getAggregateId() { return aggregateId; }
    public JSONObject getPayload() { return payload; }
}
