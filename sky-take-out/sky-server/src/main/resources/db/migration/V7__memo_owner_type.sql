-- Existing memo.user_id values do not reveal whether the owner was an employee or a customer.
-- Keep legacy rows intact with NULL owner_type; they are quarantined from both APIs until
-- an operator verifies and assigns their owner type from an independent audit source.
ALTER TABLE memo ADD COLUMN owner_type VARCHAR(10) NULL COMMENT 'ADMIN or USER; NULL means legacy ownership unknown';
CREATE INDEX idx_memo_owner ON memo (owner_type, user_id);

CREATE TABLE memo_reminder (
    memo_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_memo_reminder_user (user_id, create_time)
);
