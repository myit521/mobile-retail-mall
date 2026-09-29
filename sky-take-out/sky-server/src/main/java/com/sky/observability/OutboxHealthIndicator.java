package com.sky.observability;

import com.sky.messaging.outbox.OutboxEventMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicLong;

@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {
    private final OutboxEventMapper events;
    private final long pendingThreshold;
    private final long failedThreshold;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private volatile boolean initialized;
    private volatile boolean refreshFailed;

    @Autowired
    public OutboxHealthIndicator(OutboxEventMapper events, MeterRegistry registry,
            @Value("${sky.outbox.health.pending-threshold:1000}") long pendingThreshold,
            @Value("${sky.outbox.health.failed-threshold:0}") long failedThreshold) {
        this(events, pendingThreshold, failedThreshold);
        Gauge.builder("sky.outbox.events", pending, AtomicLong::get).tag("status", "pending").register(registry);
        Gauge.builder("sky.outbox.events", failed, AtomicLong::get).tag("status", "failed").register(registry);
    }

    OutboxHealthIndicator(OutboxEventMapper events, long pendingThreshold, long failedThreshold) {
        this.events = events;
        this.pendingThreshold = pendingThreshold;
        this.failedThreshold = failedThreshold;
    }

    @Override
    public Health health() {
        long pendingCount = pending.get();
        long failedCount = failed.get();
        Health.Builder result = !initialized || refreshFailed || pendingCount > pendingThreshold || failedCount > failedThreshold
                ? Health.down() : Health.up();
        return result.withDetail("pending", pendingCount).withDetail("failed", failedCount)
                .build();
    }

    @Scheduled(fixedDelayString = "${sky.outbox.health.refresh-ms:30000}")
    public void refreshCounts() {
        try {
            pending.set(events.countByStatus("PENDING"));
            failed.set(events.countByStatus("FAILED"));
            initialized = true;
            refreshFailed = false;
        } catch (RuntimeException failure) {
            refreshFailed = true;
        }
    }
}
