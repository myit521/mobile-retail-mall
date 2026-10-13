package com.sky.order;

import com.sky.entity.Orders;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.internal.persistence.OrderMapper;
import com.sky.order.internal.task.OrderTask;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderTaskTest {
    @Test
    void fallbackScanCancelsEveryExpiredPendingOrder() {
        OrderMapper mapper = mock(OrderMapper.class);
        OrderApplicationService service = mock(OrderApplicationService.class);
        when(mapper.selectByStatusAndOrderTimeLt(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(List.of(Orders.builder().number("expired-1").build(),
                        Orders.builder().number("expired-2").build()));
        OrderTask task = new OrderTask();
        ReflectionTestUtils.setField(task, "orderMapper", mapper);
        ReflectionTestUtils.setField(task, "orderService", service);

        task.processTimeoutOrder();

        verify(service).cancelTimeoutOrder("expired-1");
        verify(service).cancelTimeoutOrder("expired-2");
    }
}
