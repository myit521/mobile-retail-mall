package com.sky.payment.api;

import com.sky.entity.Orders;

public interface RefundApplicationService {
    void recordIntent(Orders order);
}
