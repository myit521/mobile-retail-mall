package com.sky.support;

import java.util.concurrent.atomic.AtomicLong;

public final class TestDataFactory {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestDataFactory() {
    }

    public static String nextOrderId() {
        return "test-order-" + SEQUENCE.incrementAndGet();
    }

    public static String nextEventId() {
        return "test-event-" + SEQUENCE.incrementAndGet();
    }
}
