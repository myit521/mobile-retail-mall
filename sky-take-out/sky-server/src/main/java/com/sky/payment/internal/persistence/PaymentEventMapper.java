package com.sky.payment.internal.persistence;

import com.sky.messaging.outbox.OutboxWriter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Task 8 writes pending events only; Task 9 owns delivery and retry. */
@Mapper
public interface PaymentEventMapper extends OutboxWriter {
    @Insert("INSERT INTO outbox_event(event_id, correlation_id, event_type, payload_version, aggregate_id, business_key, payload, status, created_at, updated_at) "
            + "VALUES(#{eventId}, #{correlationId}, 'ORDER_PAID', 1, #{aggregateId}, #{businessKey}, #{payload}, 'PENDING', NOW(), NOW())")
    int insertPending(@Param("eventId") String eventId, @Param("correlationId") String correlationId,
                      @Param("aggregateId") long aggregateId, @Param("businessKey") String businessKey,
                      @Param("payload") String payload);

    @Override
    default void append(String eventId, String correlationId, String eventType, int payloadVersion,
                        long aggregateId, String businessKey, String payload) {
        if (!"ORDER_PAID".equals(eventType) || payloadVersion != 1) {
            throw new IllegalArgumentException("Unsupported payment event envelope");
        }
        if (insertPending(eventId, correlationId, aggregateId, businessKey, payload) != 1) {
            throw new IllegalStateException("Payment event was not persisted");
        }
    }
}
