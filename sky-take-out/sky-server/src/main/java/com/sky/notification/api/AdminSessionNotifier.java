package com.sky.notification.api;

public interface AdminSessionNotifier {
    boolean sendTo(String clientId, String message);
}
