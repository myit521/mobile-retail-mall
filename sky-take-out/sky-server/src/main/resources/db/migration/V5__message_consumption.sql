CREATE TABLE message_consumption (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    consumer_name VARCHAR(96) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_message_consumption_consumer_event UNIQUE (consumer_name, event_id),
    INDEX idx_message_consumption_status_updated (status, updated_at),
    INDEX idx_message_consumption_business_key (business_key)
);
