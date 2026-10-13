package com.sky.order.internal.task;

import com.sky.entity.Orders;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.internal.persistence.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderApplicationService orderService;

    /**
     * 保留兜底扫描，避免 MQ 异常时超时订单长期堆积。
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void processTimeoutOrder() {
        for (Orders order : orderMapper.selectByStatusAndOrderTimeLt(
                Orders.PENDING_PAYMENT, LocalDateTime.now().minusMinutes(15))) {
            try {
                orderService.cancelTimeoutOrder(order.getNumber());
            } catch (Exception e) {
                log.error("订单超时兜底取消失败，orderNo={}", order.getNumber(), e);
            }
        }
    }
}
