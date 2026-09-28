package com.sky.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {
    public enum OrderOperation { PAYMENT, CANCEL, TIMEOUT, ACCEPT, REJECT, DELIVERY, COMPLETE }
    public enum StockOperation { DEDUCT, RELEASE }
    public enum Consumer { ORDER_TIMEOUT, ORDER_PAID_NOTIFY, ORDER_PAID_AUDIT, UNKNOWN }

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void orderConflict(OrderOperation operation) {
        counter("sky.order.conflicts", "operation", operation).increment();
    }

    public void stockFailure(StockOperation operation) {
        counter("sky.stock.failures", "operation", operation).increment();
    }

    public void duplicateCallback() {
        registry.counter("sky.payment.callback.duplicates").increment();
    }

    public void deadLetter(Consumer consumer) {
        counter("sky.messaging.dead.letters", "consumer", consumer).increment();
    }

    public void outboxPublishFailure(String reason) {
        if (!"returned".equals(reason) && !"nack".equals(reason) && !"exception".equals(reason)) {
            throw new IllegalArgumentException("Unsupported bounded failure reason");
        }
        registry.counter("sky.outbox.publish.failures", "reason", reason).increment();
    }

    private Counter counter(String name, String key, Enum<?> value) {
        return registry.counter(name, key, value.name().toLowerCase(java.util.Locale.ROOT));
    }
}
