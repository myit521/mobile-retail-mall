package com.sky.observability;

import org.slf4j.MDC;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

public final class MdcContext implements AutoCloseable {
    private static final Set<String> ALLOWED = Collections.unmodifiableSet(new LinkedHashSet<>(
            java.util.Arrays.asList("traceId", "eventId", "correlationId", "orderId")));
    private final Map<String, String> previous;

    private MdcContext(Map<String, ?> values) {
        previous = MDC.getCopyOfContextMap();
        Map<String, String> scoped = new LinkedHashMap<>();
        if (previous != null) {
            previous.forEach((key, value) -> {
                if (ALLOWED.contains(key)) {
                    scoped.put(key, SafeLogIdentifier.forLog(value, 64));
                }
            });
        }
        values.forEach((key, value) -> {
            if (ALLOWED.contains(key) && value != null) {
                scoped.put(key, SafeLogIdentifier.forLog(value, 64));
            }
        });
        MDC.setContextMap(scoped);
    }
    public static MdcContext open(Map<String, ?> values) { return new MdcContext(values); }
    public static Set<String> allowedKeys() { return ALLOWED; }
    @Override public void close() {
        MDC.clear();
        if (previous != null) {
            MDC.setContextMap(previous);
        }
    }
}
