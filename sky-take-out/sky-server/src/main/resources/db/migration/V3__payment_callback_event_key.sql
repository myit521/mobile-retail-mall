-- A platform transaction belongs to one audited order, even across application instances.
-- Conflicting legacy data intentionally fails migration and requires reconciliation.
ALTER TABLE payment_callback_log
    ADD CONSTRAINT uk_payment_transaction_id UNIQUE (transaction_id);

-- Minimal transactional writer schema. V4 extends this table for delivery/leases/retries.
CREATE TABLE outbox_event (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    correlation_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_version INT NOT NULL,
    aggregate_id BIGINT NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_outbox_business_key UNIQUE (business_key)
);
