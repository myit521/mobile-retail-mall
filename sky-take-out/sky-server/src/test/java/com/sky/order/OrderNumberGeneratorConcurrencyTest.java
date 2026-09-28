package com.sky.order;

import com.sky.order.internal.application.IdSegmentService;
import com.sky.order.internal.support.OrderNumberGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberGeneratorConcurrencyTest {

    @Test
    void concurrentRequestsNeverReceiveTheSameOrderNumber() throws Exception {
        OrderNumberGenerator generator = new OrderNumberGenerator();
        IdSegmentService segments = ignored -> new IdSegmentService.SegmentRange(1, 1_000_000);
        ReflectionTestUtils.setField(generator, "idSegmentService", segments);

        int workers = 32;
        int requestsPerWorker = 5_000;
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            for (int worker = 0; worker < workers; worker++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (int request = 0; request < requestsPerWorker; request++) {
                        numbers.add(generator.nextOrderNumber());
                    }
                    return null;
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(numbers).hasSize(workers * requestsPerWorker);
    }
}
