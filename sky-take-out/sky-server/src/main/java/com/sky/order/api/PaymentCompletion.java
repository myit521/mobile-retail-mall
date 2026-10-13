package com.sky.order.api;

import com.sky.order.api.event.OrderPaidMessage;

public record PaymentCompletion(OrderPaidMessage paidEvent, boolean compensating) {
    public static PaymentCompletion paid(OrderPaidMessage event) {
        return new PaymentCompletion(event, false);
    }

    public static PaymentCompletion compensatingRefund() {
        return new PaymentCompletion(null, true);
    }
}
