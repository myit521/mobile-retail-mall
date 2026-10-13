package com.sky.payment;

import com.sky.entity.Orders;
import com.sky.payment.internal.refund.RefundService;
import com.sky.payment.internal.refund.RefundRequestMapper;
import com.sky.payment.internal.refund.RefundRequest;
import com.sky.utils.WeChatPayUtil;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RefundServiceTest {
    @Test
    void refundIntentRequiresAnActiveOrderTransaction() {
        RefundRequestMapper mapper = mock(RefundRequestMapper.class);
        WeChatPayUtil wechat = mock(WeChatPayUtil.class);
        RefundService service = new RefundService(mapper, wechat, null);
        Orders order = Orders.builder().id(17L).number("order-17").amount(new BigDecimal("12.34")).build();

        assertThatThrownBy(() -> service.recordIntent(order))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mapper, wechat);
    }

    @Test
    void recordsFixedRefundNumberAndDefersExternalCallUntilCommit() {
        RefundRequestMapper mapper = mock(RefundRequestMapper.class);
        WeChatPayUtil wechat = mock(WeChatPayUtil.class);
        RefundService service = new RefundService(mapper, wechat, null);
        Orders order = Orders.builder().id(17L).number("order-17").amount(new BigDecimal("12.34")).build();
        when(mapper.insert(17L, "order-17", "R17", new BigDecimal("12.34"))).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.recordIntent(order);
            verifyNoInteractions(wechat);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void repeatedLateCallbackReusesExistingRefundIntent() {
        RefundRequestMapper mapper = mock(RefundRequestMapper.class);
        RefundService service = new RefundService(mapper, mock(WeChatPayUtil.class), null);
        Orders order = Orders.builder().id(170L).number("order-170").amount(new BigDecimal("12.34")).build();
        RefundRequest existing = new RefundRequest();
        existing.setOrderId(170L);
        existing.setOrderNumber("order-170");
        existing.setAmount(new BigDecimal("12.34"));
        when(mapper.find("R170")).thenReturn(existing);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.recordIntent(order);
            verify(mapper, never()).insert(anyLong(), anyString(), anyString(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
