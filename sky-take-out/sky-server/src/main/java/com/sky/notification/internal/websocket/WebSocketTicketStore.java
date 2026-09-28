package com.sky.notification.internal.websocket;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface WebSocketTicketStore {
    void put(String ticket, Record record, Duration ttl);

    Optional<Record> take(String ticket);

    record Record(String audience, long principalId, String adminSessionId, Instant authenticationExpiresAt) {
        public static Record admin(long employeeId, String adminSessionId, Instant authenticationExpiresAt) {
            return new Record(WebSocketTicketService.ADMIN_AUDIENCE, employeeId, adminSessionId, authenticationExpiresAt);
        }
    }
}
