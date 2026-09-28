package com.sky.notification.internal.websocket;

import com.sky.auth.api.TokenSessionService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class WebSocketTicketService {
    static final String ADMIN_AUDIENCE = "admin-order-notification";
    private final WebSocketTicketStore store;
    private final TokenSessionService tokenSessions;
    private final WebSocketTicketProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public WebSocketTicketService(WebSocketTicketStore store, TokenSessionService tokenSessions,
                                  WebSocketTicketProperties properties) {
        this(store, tokenSessions, properties, Clock.systemUTC());
    }

    public WebSocketTicketService(WebSocketTicketStore store, TokenSessionService tokenSessions,
                                  WebSocketTicketProperties properties, Clock clock) {
        this.store = store;
        this.tokenSessions = tokenSessions;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(long employeeId, String adminSessionId, Instant authenticationExpiresAt) {
        if (!authenticationExpiresAt.isAfter(clock.instant())
                || !tokenSessions.isAdminSessionValid(adminSessionId)) {
            throw new IllegalArgumentException("Authentication expired");
        }
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        store.put(ticket, WebSocketTicketStore.Record.admin(employeeId, adminSessionId, authenticationExpiresAt),
                properties.getTicketTtl());
        return ticket;
    }

    public Optional<WebSocketPrincipal> consume(String ticket) {
        Optional<WebSocketTicketStore.Record> taken = store.take(ticket);
        if (taken.isEmpty()) return Optional.empty();
        WebSocketTicketStore.Record record = taken.get();
        if (!ADMIN_AUDIENCE.equals(record.audience())
                || !record.authenticationExpiresAt().isAfter(clock.instant())
                || !tokenSessions.isAdminSessionValid(record.adminSessionId())) return Optional.empty();
        return Optional.of(new WebSocketPrincipal(String.valueOf(record.principalId()),
                record.adminSessionId(), record.authenticationExpiresAt()));
    }
}
