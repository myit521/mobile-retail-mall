package com.sky.notification.api;

/**
 * Public notification boundary used for order-facing broadcasts.
 */
public interface OrderNotificationPort {
    void broadcast(String message);

    void sendTo(String message, String clientId);
}
