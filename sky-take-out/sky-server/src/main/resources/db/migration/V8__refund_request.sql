CREATE TABLE refund_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    out_refund_no VARCHAR(64) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_refund_order UNIQUE (order_id),
    CONSTRAINT uk_refund_number UNIQUE (out_refund_no),
    INDEX idx_refund_retry (status, updated_at)
);
