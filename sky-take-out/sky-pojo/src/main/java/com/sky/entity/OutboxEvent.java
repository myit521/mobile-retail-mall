package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    private String eventId;
    private String correlationId;
    private String eventType;
    private Integer payloadVersion;
    private Long aggregateId;
    private String businessKey;
    private String payload;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private String leaseOwner;
    private LocalDateTime leaseExpiresAt;
    private String lastError;
    private LocalDateTime claimedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
