package com.sky.notification.internal.websocket;

import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;

@Component
public class WebSocketEndpointInstaller {
    private final WebSocketServer.Configuration installed;

    public WebSocketEndpointInstaller(WebSocketSessionRegistry sessions,
                                      WebSocketTicketService tickets) {
        installed = WebSocketServer.configure(sessions, tickets);
    }

    @PreDestroy
    public void uninstall() {
        WebSocketServer.clearConfiguration(installed);
    }
}
