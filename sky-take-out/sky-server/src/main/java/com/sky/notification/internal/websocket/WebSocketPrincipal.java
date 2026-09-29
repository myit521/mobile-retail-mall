package com.sky.notification.internal.websocket;

import java.time.Instant;

public record WebSocketPrincipal(String clientId, String adminSessionId, Instant authenticationExpiresAt) {
}
