package com.sky.notification.internal.websocket;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class RedisWebSocketTicketStore implements WebSocketTicketStore {
    private static final DefaultRedisScript<String> TAKE_SCRIPT = new DefaultRedisScript<>(
            "local value=redis.call('GET',KEYS[1]); if value then redis.call('DEL',KEYS[1]); end; return value",
            String.class);
    private final StringRedisTemplate redis;
    private final WebSocketTicketProperties properties;

    public RedisWebSocketTicketStore(StringRedisTemplate redis, WebSocketTicketProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void put(String ticket, Record record, Duration ttl) {
        redis.opsForValue().set(key(ticket), encode(record), ttl);
    }

    @Override
    public Optional<Record> take(String ticket) {
        if (ticket == null || ticket.length() < 32 || ticket.length() > 128) return Optional.empty();
        String value = redis.execute(TAKE_SCRIPT, Collections.singletonList(key(ticket)));
        return value == null ? Optional.empty() : decode(value);
    }

    private String key(String ticket) {
        return properties.getTicketKeyPrefix() + ticket;
    }

    private String encode(Record record) {
        String session = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(record.adminSessionId().getBytes(StandardCharsets.UTF_8));
        return record.audience() + "|" + record.principalId() + "|" + session + "|"
                + record.authenticationExpiresAt().toEpochMilli();
    }

    private Optional<Record> decode(String encoded) {
        try {
            String[] parts = encoded.split("\\|", -1);
            if (parts.length != 4) return Optional.empty();
            String session = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            return Optional.of(new Record(parts[0], Long.parseLong(parts[1]), session,
                    Instant.ofEpochMilli(Long.parseLong(parts[3]))));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }
}
