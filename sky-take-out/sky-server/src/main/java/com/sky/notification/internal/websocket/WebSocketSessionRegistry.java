package com.sky.notification.internal.websocket;

import com.sky.auth.api.TokenSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WebSocketSessionRegistry {
    private final Map<String, Connection> sessions = new ConcurrentHashMap<>();
    private final TokenSessionService tokenSessions;
    private final Clock clock;

    @Autowired
    public WebSocketSessionRegistry(TokenSessionService tokenSessions) {
        this(tokenSessions, Clock.systemUTC());
    }

    public WebSocketSessionRegistry(TokenSessionService tokenSessions, Clock clock) {
        this.tokenSessions = tokenSessions;
        this.clock = clock;
    }

    public void open(WebSocketPrincipal principal, Session session) {
        Connection replacement = new Connection(principal, session);
        Connection displaced = sessions.put(principal.clientId(), replacement);
        if (displaced != null && displaced.session != session) closeTransport(displaced.session, "replaced");
    }

    public void close(Session session) {
        sessions.forEach((clientId, connection) -> {
            if (connection.session == session) sessions.remove(clientId, connection);
        });
    }

    public boolean sendTo(String clientId, String message) {
        Connection connection = sessions.get(clientId);
        if (connection == null) return false;
        synchronized (connection.sendLock) {
            if (sessions.get(clientId) != connection) return false;
            if (!authorized(connection) || !connection.session.isOpen()) {
                sessions.remove(clientId, connection);
                closeTransport(connection.session, "authentication expired");
                return false;
            }
            try {
                connection.session.getBasicRemote().sendText(message);
                return true;
            } catch (Exception exception) {
                sessions.remove(clientId, connection);
                closeTransport(connection.session, "send failed");
                log.warn("WebSocket notification failed, reason={}", exception.getClass().getSimpleName());
                return false;
            }
        }
    }

    public void broadcast(String message) {
        sessions.keySet().forEach(clientId -> sendTo(clientId, message));
    }

    public int size() { return sessions.size(); }

    public boolean contains(String clientId, Session session) {
        Connection connection = sessions.get(clientId);
        return connection != null && connection.session == session;
    }

    private boolean authorized(Connection connection) {
        return connection.principal.authenticationExpiresAt().isAfter(clock.instant())
                && tokenSessions.isAdminSessionValid(connection.principal.adminSessionId());
    }

    private void closeTransport(Session session, String reason) {
        try {
            if (session.isOpen()) session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, reason));
        } catch (Exception failure) {
            log.debug("WebSocket close failed, reason={}", failure.getClass().getSimpleName());
        }
    }

    private static final class Connection {
        private final WebSocketPrincipal principal;
        private final Session session;
        private final Object sendLock = new Object();
        private Connection(WebSocketPrincipal principal, Session session) {
            this.principal = principal;
            this.session = session;
        }
    }
}
