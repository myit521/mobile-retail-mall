package com.sky.notification.internal;

import com.sky.notification.api.OrderNotificationPort;
import com.sky.notification.internal.websocket.WebSocketSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class WebSocketOrderNotificationAdapter implements OrderNotificationPort {
    private final WebSocketSessionRegistry sessions;

    public WebSocketOrderNotificationAdapter(WebSocketSessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public void broadcast(String message) {
        afterCommit(() -> sessions.broadcast(message));
    }

    @Override
    public void sendTo(String message, String clientId) {
        afterCommit(() -> sessions.sendTo(clientId, message));
    }

    private void afterCommit(Runnable notification) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bestEffort(notification);
                }
            });
            return;
        }
        bestEffort(notification);
    }

    private void bestEffort(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException exception) {
            log.warn("WebSocket notification side effect failed, errorType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
