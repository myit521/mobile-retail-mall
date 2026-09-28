package com.sky.notification.internal.websocket;

import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.util.Optional;

@ServerEndpoint("/ws")
@Slf4j
public class WebSocketServer {
    private static volatile Configuration configuration;

    public static Configuration configure(WebSocketSessionRegistry registry, WebSocketTicketService tickets) {
        Configuration installed = new Configuration(registry, tickets);
        configuration = installed;
        return installed;
    }

    public static void clearConfiguration(Configuration installed) {
        if (configuration == installed) configuration = null;
    }

    public static void clearConfiguration() { configuration = null; }
    
    @OnOpen
    public void onOpen(Session session) {
        Configuration current = configuration;
        String ticket = first(session, "ticket");
        Optional<WebSocketPrincipal> principal = current == null ? Optional.empty() : current.tickets.consume(ticket);
        if (principal.isEmpty()) {
            closeUnauthorized(session);
            return;
        }
        current.sessions.open(principal.get(), session);
        log.info("WebSocket connection established, clientId={}", principal.get().clientId());
    }
    
    @OnMessage
    public void onMessage(String message) {
        // 收到消息时的处理
    }
    
    @OnClose
    public void onClose(Session session) {
        Configuration current = configuration;
        if (current != null) current.sessions.close(session);
        log.info("WebSocket connection closed");
    }
    
    @OnError
    public void onError(Session session, Throwable error) {
        Configuration current = configuration;
        if (current != null) current.sessions.close(session);
        log.warn("WebSocket connection error, errorType={}", error.getClass().getSimpleName());
    }

    private void closeUnauthorized(Session session) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "authentication required"));
        } catch (Exception closeFailure) {
            log.warn("Unable to close rejected WebSocket, errorType={}", closeFailure.getClass().getSimpleName());
        }
    }

    private String first(Session session, String name) {
        java.util.List<String> values = session.getRequestParameterMap().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    public static final class Configuration {
        private final WebSocketSessionRegistry sessions;
        private final WebSocketTicketService tickets;
        private Configuration(WebSocketSessionRegistry sessions, WebSocketTicketService tickets) {
            this.sessions = sessions;
            this.tickets = tickets;
        }
    }

 }
