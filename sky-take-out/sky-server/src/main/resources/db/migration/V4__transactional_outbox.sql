-- Extend the V3 atomic-write table; do not introduce a second event store.
ALTER TABLE outbox_event
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN next_attempt_at DATETIME NULL AFTER attempt_count,
    ADD COLUMN lease_owner VARCHAR(64) NULL AFTER next_attempt_at,
    ADD COLUMN lease_expires_at DATETIME NULL AFTER lease_owner,
    ADD COLUMN last_error VARCHAR(512) NULL AFTER lease_expires_at,
    ADD INDEX idx_outbox_due (status, next_attempt_at, lease_expires_at, created_at);
