package com.mall.service;

import com.mall.entity.Employee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Token 会话管理服务
 */
@Service
public class TokenSessionService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Redis key 前缀
    private static final String ADMIN_SESSION_PREFIX = "login:admin:";
    private static final String USER_SESSION_PREFIX = "login:user:";
    
    // 会话过期时间（秒）- 默认 2 小时，实际从 JwtProperties 读取并换算
    private static final long DEFAULT_SESSION_TTL_SECONDS = 7200;

    /**
     * 构建管理员会话 Redis key
     */
    public String buildAdminSessionKey(String sessionId) {
        return ADMIN_SESSION_PREFIX + sessionId;
    }

    /**
     * 构建用户会话 Redis key
     */
    public String buildUserSessionKey(String sessionId) {
        return USER_SESSION_PREFIX + sessionId;
    }

    /**
     * 创建管理员会话
     * @param employee 员工信息
     * @param ttlMillis JWT 过期时间（毫秒），从 JwtProperties.adminTtl 读取
     * @return sessionId
     */
    public String createAdminSession(Employee employee, long ttlMillis) {
        String sessionId = UUID.randomUUID().toString();
        String redisKey = buildAdminSessionKey(sessionId);
        
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("id", String.valueOf(employee.getId()));
        sessionData.put("username", employee.getUsername());
        sessionData.put("role", employee.getRole());
        sessionData.put("loginTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        stringRedisTemplate.opsForHash().putAll(redisKey, sessionData);
        // 直接使用毫秒单位，避免精度丢失
        stringRedisTemplate.expire(redisKey, ttlMillis, TimeUnit.MILLISECONDS);
        
        return sessionId;
    }
    
    /**
     * 创建管理员会话（兼容旧调用，使用默认 TTL）
     */
    public String createAdminSession(Employee employee) {
        return createAdminSession(employee, DEFAULT_SESSION_TTL_SECONDS * 1000);
    }

    /**
     * 创建用户会话
     * @param userId 用户 ID
     * @param username 用户名
     * @param ttlMillis JWT 过期时间（毫秒），从 JwtProperties.userTtl 读取
     * @return sessionId
     */
    public String createUserSession(Long userId, String username, long ttlMillis) {
        String sessionId = UUID.randomUUID().toString();
        String redisKey = buildUserSessionKey(sessionId);
        
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("id", String.valueOf(userId));
        sessionData.put("username", username);
        sessionData.put("loginTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        stringRedisTemplate.opsForHash().putAll(redisKey, sessionData);
        // 直接使用毫秒单位，避免精度丢失
        stringRedisTemplate.expire(redisKey, ttlMillis, TimeUnit.MILLISECONDS);
        
        return sessionId;
    }
    
    /**
     * 创建用户会话（兼容旧调用，使用默认 TTL）
     */
    public String createUserSession(Long userId, String username) {
        return createUserSession(userId, username, DEFAULT_SESSION_TTL_SECONDS * 1000);
    }

    /**
     * 验证管理员会话是否有效
     * @param sessionId 会话 ID
     * @return true/false
     */
    public boolean isAdminSessionValid(String sessionId) {
        String redisKey = buildAdminSessionKey(sessionId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey));
    }

    /**
     * 验证用户会话是否有效
     * @param sessionId 会话 ID
     * @return true/false
     */
    public boolean isUserSessionValid(String sessionId) {
        String redisKey = buildUserSessionKey(sessionId);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey));
    }

    /**
     * 删除管理员会话（登出时使用）
     * @param sessionId 会话 ID
     */
    public void removeAdminSession(String sessionId) {
        String redisKey = buildAdminSessionKey(sessionId);
        stringRedisTemplate.delete(redisKey);
    }

    /**
     * 删除用户会话（登出时使用）
     * @param sessionId 会话 ID
     */
    public void removeUserSession(String sessionId) {
        String redisKey = buildUserSessionKey(sessionId);
        stringRedisTemplate.delete(redisKey);
    }

    /**
     * 获取会话中的角色信息
     * @param sessionId 会话 ID
     * @param isAdminSession 是否为管理员会话
     * @return 角色信息
     */
    public String getRoleFromSession(String sessionId, boolean isAdminSession) {
        String redisKey = isAdminSession ? buildAdminSessionKey(sessionId) : buildUserSessionKey(sessionId);
        Object role = stringRedisTemplate.opsForHash().get(redisKey, "role");
        return role != null ? role.toString() : null;
    }
}
