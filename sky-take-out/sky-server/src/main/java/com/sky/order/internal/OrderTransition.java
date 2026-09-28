package com.sky.order.internal;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;

public final class OrderTransition {
    private OrderTransition() {
    }

    public static void requireAllowed(int from, int to) {
        boolean active = from >= Orders.PENDING_PAYMENT && from <= Orders.READY_FOR_PICKUP;
        if (!active || (to != from + 1 && to != Orders.CANCELLED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }
}
