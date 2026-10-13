package com.sky.order;

import com.sky.entity.Orders;
import com.sky.order.api.PaymentCompletion;
import com.sky.order.internal.application.OrderServiceImpl;
import com.sky.order.internal.persistence.OrderMapper;
import com.sky.payment.api.RefundApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LatePaymentRaceTest {
    @Test
    void cancellationWinningAfterInitialReadQueuesRefundForVerifiedPayment() {
        OrderMapper orders = mock(OrderMapper.class);
        RefundApplicationService refunds = mock(RefundApplicationService.class);
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orders);
        ReflectionTestUtils.setField(service, "refundService", refunds);
        Orders pending = Orders.builder().id(7L).number("order-7").amount(new BigDecimal("12.34"))
                .status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).build();
        Orders canceled = Orders.builder().id(7L).number("order-7").amount(new BigDecimal("12.34"))
                .status(Orders.CANCELLED).payStatus(Orders.UN_PAID).build();
        when(orders.getByNumber("order-7")).thenReturn(pending);
        when(orders.transition(eq(7L), isNull(), eq(Orders.PENDING_PAYMENT),
                eq(Orders.PENDING_PROCESS), any(LocalDateTime.class))).thenReturn(0);
        when(orders.selectForUpdate(7L, null)).thenReturn(canceled);
        when(orders.updateTransitionDetails(any(Orders.class))).thenReturn(1);

        PaymentCompletion completion = service.completeVerifiedPayment("order-7", new BigDecimal("12.34"));

        assertThat(completion.compensating()).isTrue();
        verify(orders).updateTransitionDetails(argThat(update ->
                update.getId().equals(7L) && update.getPayStatus().equals(Orders.PAID)));
        verify(refunds).recordIntent(canceled);
    }
}
