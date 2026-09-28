package com.sky.messaging.outbox;

/** Public write boundary used from the surrounding business transaction. */
public interface OutboxWriter {
    void append(String eventId, String correlationId, String eventType, int payloadVersion,
                long aggregateId, String businessKey, String payload);
}
