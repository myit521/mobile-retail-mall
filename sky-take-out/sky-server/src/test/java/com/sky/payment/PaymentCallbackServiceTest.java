package com.sky.payment;

import com.sky.entity.PaymentCallbackLog;
import com.sky.order.api.OrderApplicationService;
import com.sky.payment.api.PaymentCallbackCommand;
import com.sky.payment.internal.PaymentCallbackService;
import com.sky.payment.internal.persistence.PaymentCallbackLogMapper;
import com.sky.payment.internal.persistence.PaymentEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaymentCallbackServiceTest {
    private final PaymentCallbackLogMapper callbacks = mock(PaymentCallbackLogMapper.class);
    private final OrderApplicationService orders = mock(OrderApplicationService.class);
    private final PaymentEventMapper events = mock(PaymentEventMapper.class);
    private final PaymentCallbackService service = new PaymentCallbackService(callbacks, orders, events);

    @Test
    void invalidCommandCannotReachPersistence() {
        for (PaymentCallbackCommand command : new PaymentCallbackCommand[] {null,
                new PaymentCallbackCommand("", "tx", 1234), new PaymentCallbackCommand("order", " ", 1234),
                new PaymentCallbackCommand("order", "tx", null), new PaymentCallbackCommand("order", "tx", -1)}) {
            assertThatThrownBy(() -> service.handle(command)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(callbacks, orders, events);
    }

    @Test
    void callbackInsertMustSucceedBeforeOrderMutation() {
        when(callbacks.insert(any())).thenReturn(0);
        assertThatThrownBy(() -> service.handle(new PaymentCallbackCommand("order", "tx", 1234)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(orders, events);
    }

    @Test
    void committedKeyCannotBeReboundToAnotherOrder() {
        when(callbacks.insert(any())).thenThrow(new DuplicateKeyException("existing"));
        when(callbacks.getByTransactionIdForShare("tx")).thenReturn(PaymentCallbackLog.builder()
                .transactionId("tx").outTradeNo("someone-elses-order").callbackStatus("SUCCESS").build());
        assertThatThrownBy(() -> service.handle(new PaymentCallbackCommand("order", "tx", 1234)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(orders, events);
    }
}
