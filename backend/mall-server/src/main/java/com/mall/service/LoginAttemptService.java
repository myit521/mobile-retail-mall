package com.mall.service;

/**
 * 登录限流服务 - 基于 Redis 实现暴力破解防护
 *
 * 策略：
 *   同一账号连续登录失败 MAX_ATTEMPTS 次 → 锁定 LOCK_MINUTES 分钟
 *   登录成功后立即清除失败计数
 */
public interface LoginAttemptService {

    /**
     * 记录一次登录失败
     * @param username 账号
     */
    void recordFailure(String username);

    /**
     * 登录成功后清除失败记录
     * @param username 账号
     */
    void clearFailure(String username);

    /**
     * 检查账号是否被锁定，如已锁定则抛出 TooManyAttemptsException
     * @param username 账号
     */
    void checkLocked(String username);

    /**
     * 查询账号剩余锁定秒数（未锁定返回 0）
     * @param username 账号
     * @return 剩余秒数
     */
    long getLockedSeconds(String username);
}
