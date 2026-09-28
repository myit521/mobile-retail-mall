package com.sky.messaging.consumer;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageConsumptionService {
    private static final int ERROR_LIMIT = 512;
    private final JdbcTemplate jdbc;

    public MessageConsumptionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean consume(String consumerName, String eventId, String businessKey, Runnable action) {
        requireBounded(consumerName, "consumerName", 96);
        requireBounded(eventId, "eventId", 64);
        requireBounded(businessKey, "businessKey", 128);
        try {
            jdbc.update("INSERT INTO message_consumption "
                            + "(consumer_name,event_id,business_key,status,created_at,updated_at) "
                            + "VALUES (?,?,?,'PROCESSING',NOW(),NOW())",
                    consumerName, eventId, businessKey);
        } catch (DuplicateKeyException duplicate) {
            ExistingConsumption existing = existing(consumerName, eventId);
            if (existing == null || !businessKey.equals(existing.businessKey)) {
                throw new IllegalStateException("Event identity is bound to a different business key");
            }
            if ("COMPLETED".equals(existing.status)) {
                return false;
            }
            throw new IllegalStateException("Existing event consumption is not completed");
        }
        action.run();
        jdbc.update("UPDATE message_consumption SET status='COMPLETED', error=NULL, updated_at=NOW() "
                + "WHERE consumer_name=? AND event_id=?", consumerName, eventId);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String consumerName, String eventId, String businessKey, String error) {
        requireBounded(consumerName, "consumerName", 96);
        requireBounded(eventId, "eventId", 64);
        requireBounded(businessKey, "businessKey", 128);
        String safeError = sanitize(error);
        try {
            jdbc.update("INSERT INTO message_consumption "
                            + "(consumer_name,event_id,business_key,status,error,created_at,updated_at) "
                            + "VALUES (?,?,?,'FAILED',?,NOW(),NOW())",
                    consumerName, eventId, businessKey, safeError);
        } catch (DuplicateKeyException duplicate) {
            ExistingConsumption existing = existing(consumerName, eventId);
            if (existing == null || !businessKey.equals(existing.businessKey)) {
                throw new IllegalStateException("Event identity is bound to a different business key");
            }
            if (!"FAILED".equals(existing.status)) {
                throw new IllegalStateException("Existing event consumption is not failed");
            }
            jdbc.update("UPDATE message_consumption SET error=?, updated_at=NOW() "
                    + "WHERE consumer_name=? AND event_id=? AND business_key=? AND status='FAILED'",
                    safeError, consumerName, eventId, businessKey);
        }
    }

    public String sanitize(String error) {
        if (error == null) return "processing-failed";
        String safe = error.replaceAll("(?i)(password|token|secret|authorization|ciphertext)\\s*[:=]\\s*[^,;\\s]+", "$1=[redacted]")
                .replaceAll("[\\r\\n\\t]", " ");
        return safe.substring(0, Math.min(ERROR_LIMIT, safe.length()));
    }

    private void requireBounded(String value, String name, int maxLength) {
        if (value == null || value.trim().isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
        }
    }

    private ExistingConsumption existing(String consumerName, String eventId) {
        return jdbc.queryForObject(
                "SELECT status,business_key FROM message_consumption WHERE consumer_name=? AND event_id=?",
                (resultSet, rowNumber) -> new ExistingConsumption(
                        resultSet.getString("status"), resultSet.getString("business_key")),
                consumerName, eventId);
    }

    private static final class ExistingConsumption {
        private final String status;
        private final String businessKey;

        private ExistingConsumption(String status, String businessKey) {
            this.status = status;
            this.businessKey = businessKey;
        }
    }
}
