package com.mall.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.PostConstruct;

@Configuration
@Slf4j
/**
 * 配置 RedisTemplate
 */
public class RedisConfiguration {
    
    private RedisConnectionFactory redisConnectionFactory;
    
    public RedisConfiguration(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }
    
    /**
     * 应用启动时检查 Redis 连接
     */
    @PostConstruct
    public void checkRedisConnection() {
        try {
            log.info("正在检查 Redis 连接...");
            // 真正尝试获取 Redis 连接
            redisConnectionFactory.getConnection();
            log.info("✅ Redis 连接检查通过");
        } catch (Exception e) {
            log.error("❌ Redis 连接失败，请检查 Redis 服务是否启动：{}", e.getMessage());
            log.error("错误详情：{}", e.getCause() != null ? e.getCause().getMessage() : "未知");
            throw new RuntimeException("Redis 连接失败，应用无法启动", e);
        }
    }
    /**
     * 配置redis
     */
    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate redisTemplate = new RedisTemplate();
        //设置redis连接工厂
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        //设置redis的key的序列化方式
        redisTemplate.setKeySerializer(new StringRedisSerializer());
//        //设置redis的value的序列化方式
//        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }
}
