package com.mall.service.impl;

import com.mall.constant.MessageConstant;
import com.mall.exception.TooManyAttemptsException;
import com.mall.service.LoginAttemptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录限流服务实现
 */
@Service
@Slf4j
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /** 最大失败次数 */
    private static final int MAX_ATTEMPTS = 5;

    /** 锁定时长（分钟） */
    private static final int LOCK_MINUTES = 15;

    /** 失败计数 key 前缀 */
    private static final String ATTEMPT_KEY_PREFIX = "login:attempt:";

    /** 锁定标记 key 前缀 */
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void checkLocked(String username) {
        String lockKey = LOCK_KEY_PREFIX + username;
        Long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);

        if (ttl != null && ttl > 0) {
            long minutes = (ttl + 59) / 60; // 向上取整到分钟
            log.warn("账号 [{}] 已被锁定，剩余 {} 秒", username, ttl);
            throw new TooManyAttemptsException(
                    String.format(MessageConstant.LOGIN_TOO_MANY_ATTEMPTS, minutes));
        }
    }

    @Override
    public void recordFailure(String username) {
        String attemptKey = ATTEMPT_KEY_PREFIX + username;

        // 失败次数 +1，并设置计数窗口（与锁定时长一致，避免永久堆积）
        Long count = stringRedisTemplate.opsForValue()
                .increment(attemptKey);

        if (count == 1) {
            // 第一次失败，设置过期时间
            stringRedisTemplate.expire(attemptKey, LOCK_MINUTES, TimeUnit.MINUTES);
        }

        log.info("账号 [{}] 登录失败，当前失败次数：{}", username, count);

        if (count != null && count >= MAX_ATTEMPTS) {
            // 达到上限：写入锁定 key，清除计数 key
            String lockKey = LOCK_KEY_PREFIX + username;
            stringRedisTemplate.opsForValue()
                    .set(lockKey, "locked", LOCK_MINUTES, TimeUnit.MINUTES);
            stringRedisTemplate.delete(attemptKey);

            log.warn("账号 [{}] 失败次数达到上限，锁定 {} 分钟", username, LOCK_MINUTES);
            throw new TooManyAttemptsException(
                    String.format(MessageConstant.LOGIN_TOO_MANY_ATTEMPTS, LOCK_MINUTES));
        }
    }

    @Override
    public void clearFailure(String username) {
        stringRedisTemplate.delete(ATTEMPT_KEY_PREFIX + username);
        log.debug("账号 [{}] 登录成功，清除失败计数", username);
    }

    @Override
    public long getLockedSeconds(String username) {
        Long ttl = stringRedisTemplate.getExpire(LOCK_KEY_PREFIX + username, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }
}
