package com.sky.auth.api;

import com.sky.entity.Employee;

/**
 * Public authentication-session boundary for controllers and HTTP interceptors.
 */
public interface TokenSessionService {
    String buildAdminSessionKey(String sessionId);

    String buildUserSessionKey(String sessionId);

    String createAdminSession(Employee employee, long ttlMillis);

    String createAdminSession(Employee employee);

    String createUserSession(Long userId, String username, long ttlMillis);

    String createUserSession(Long userId, String username);

    boolean isAdminSessionValid(String sessionId);

    boolean isUserSessionValid(String sessionId);

    void removeAdminSession(String sessionId);

    void removeUserSession(String sessionId);

    String getRoleFromSession(String sessionId, boolean isAdminSession);
}
