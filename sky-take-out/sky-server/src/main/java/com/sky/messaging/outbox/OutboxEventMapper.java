package com.sky.messaging.outbox;

import com.sky.entity.OutboxEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OutboxEventMapper {
    @Insert("INSERT INTO outbox_event(event_id, correlation_id, event_type, payload_version, aggregate_id, business_key, payload, status, created_at, updated_at) "
            + "VALUES(#{eventId}, #{correlationId}, #{eventType}, #{payloadVersion}, #{aggregateId}, #{businessKey}, #{payload}, 'PENDING', NOW(), NOW())")
    int insertPending(OutboxEvent event);

    @Select("SELECT event_id FROM outbox_event "
            + "WHERE ((status IN ('PENDING') AND (next_attempt_at IS NULL OR next_attempt_at <= #{now})) "
            + "OR (status = 'SENDING' AND lease_expires_at <= #{now})) "
            + "ORDER BY created_at LIMIT 1")
    String findDueEventId(@Param("now") LocalDateTime now);

    @Update("UPDATE outbox_event SET status='SENDING', lease_owner=#{owner}, lease_expires_at=#{expires}, last_error=NULL, updated_at=#{now} "
            + "WHERE event_id=#{eventId} AND ((status='PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= #{now})) "
            + "OR (status='SENDING' AND lease_expires_at <= #{now}))")
    int acquireLease(@Param("eventId") String eventId, @Param("owner") String owner,
                     @Param("now") LocalDateTime now, @Param("expires") LocalDateTime expires);

    @Select("SELECT *, #{claimedAt} AS claimed_at FROM outbox_event WHERE event_id=#{eventId} AND status='SENDING' AND lease_owner=#{owner}")
    OutboxEvent getClaimed(@Param("eventId") String eventId, @Param("owner") String owner,
                           @Param("claimedAt") LocalDateTime claimedAt);

    default OutboxEvent claimNext(String owner, LocalDateTime now, LocalDateTime expires) {
        String eventId = findDueEventId(now);
        if (eventId == null || acquireLease(eventId, owner, now, expires) != 1) {
            return null;
        }
        return getClaimed(eventId, owner, now);
    }

    @Update("UPDATE outbox_event SET status='SENT', lease_owner=NULL, lease_expires_at=NULL, next_attempt_at=NULL, last_error=NULL, updated_at=NOW() "
            + "WHERE event_id=#{eventId} AND status='SENDING' AND lease_owner=#{owner}")
    int markSent(@Param("eventId") String eventId, @Param("owner") String owner);

    @Update("UPDATE outbox_event SET status='PENDING', attempt_count=#{attempts}, next_attempt_at=#{nextAttempt}, "
            + "lease_owner=NULL, lease_expires_at=NULL, last_error=#{error}, updated_at=NOW() "
            + "WHERE event_id=#{eventId} AND status='SENDING' AND lease_owner=#{owner}")
    int markRetry(@Param("eventId") String eventId, @Param("owner") String owner,
                  @Param("attempts") int attempts, @Param("nextAttempt") LocalDateTime nextAttempt,
                  @Param("error") String error);

    @Update("UPDATE outbox_event SET status='FAILED', attempt_count=#{attempts}, next_attempt_at=NULL, "
            + "lease_owner=NULL, lease_expires_at=NULL, last_error=#{error}, updated_at=NOW() "
            + "WHERE event_id=#{eventId} AND status='SENDING' AND lease_owner=#{owner}")
    int markFailed(@Param("eventId") String eventId, @Param("owner") String owner,
                   @Param("attempts") int attempts, @Param("error") String error);

    @Select("SELECT COUNT(*) FROM outbox_event USE INDEX (idx_outbox_due) WHERE status=#{status}")
    long countByStatus(@Param("status") String status);
}
