package com.mall.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Spring Cache + Redis 缓存配置
 */
@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    /**
     * 配置缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("初始化 Redis 缓存管理器...");

        // 配置 JSON 序列化器
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        objectMapper.registerModule(new JavaTimeModule()); // 支持 LocalDateTime

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // 默认缓存配置：1小时过期
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues(); // 不缓存空值（防止缓存穿透可单独处理）

        // 针对不同缓存名称设置不同过期时间
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 商品列表缓存：30分钟（数据变化较频繁）
        cacheConfigurations.put("product", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 手机型号缓存：2小时（数据变化较少）
        cacheConfigurations.put("phoneModel", defaultConfig.entryTtl(Duration.ofHours(2)));

        // 商品位置缓存：1小时
        cacheConfigurations.put("productLocation", defaultConfig.entryTtl(Duration.ofHours(1)));

        // 分类缓存：2小时（数据变化很少）
        cacheConfigurations.put("category", defaultConfig.entryTtl(Duration.ofHours(2)));

        // 店铺状态缓存：5分钟（实时性要求较高）
        cacheConfigurations.put("shopStatus", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
