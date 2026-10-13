package com.sky.payment;

import com.sky.payment.internal.refund.RefundRequest;
import com.sky.payment.internal.refund.RefundRequestMapper;
import com.sky.payment.internal.refund.RefundSettlementService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RefundSettlementServiceTest {
    private final RefundRequestMapper refunds = mock(RefundRequestMapper.class);
    private final RefundSettlementService settlement = new RefundSettlementService(refunds);

    @Test
    void confirmedSuccessMarksOrderRefundedBeforeFinalizingRequest() {
        when(refunds.lock("R7")).thenReturn(request());
        when(refunds.markOrderRefunded(7L)).thenReturn(1);

        settlement.recordResult("R7", "order-7", new BigDecimal("12.34"), "SUCCESS");

        InOrder sequence = inOrder(refunds);
        sequence.verify(refunds).lock("R7");
        sequence.verify(refunds).markOrderRefunded(7L);
        sequence.verify(refunds).updateStatus("R7", "SUCCESS");
    }

    @Test
    void failedRefundDoesNotMarkOrderAsRefunded() {
        when(refunds.lock("R7")).thenReturn(request());

        settlement.recordResult("R7", "order-7", new BigDecimal("12.34"), "CLOSED");

        verify(refunds, never()).markOrderRefunded(anyLong());
        verify(refunds).updateStatus("R7", "CLOSED");
    }

    @Test
    void mismatchedAmountCannotSettleAnotherRefund() {
        when(refunds.lock("R7")).thenReturn(request());

        assertThatThrownBy(() -> settlement.recordResult("R7", "order-7",
                new BigDecimal("1.00"), "SUCCESS")).isInstanceOf(IllegalArgumentException.class);

        verify(refunds, never()).markOrderRefunded(anyLong());
        verify(refunds, never()).updateStatus(anyString(), anyString());
    }

    private RefundRequest request() {
        RefundRequest request = new RefundRequest();
        request.setOrderId(7L);
        request.setOrderNumber("order-7");
        request.setAmount(new BigDecimal("12.34"));
        request.setStatus("PROCESSING");
        return request;
    }
}
