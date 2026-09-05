package com.sky.notification.internal.messaging;

import com.alibaba.fastjson.JSON;
import com.sky.constant.RabbitMQConstant;
import com.sky.notification.api.OrderNotificationPort;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.api.event.OrderPaidMessage;
import com.sky.order.api.event.OrderTimeoutMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class OrderEventConsumer {

    @Autowired
    private OrderApplicationService orderService;
    @Autowired
    private OrderNotificationPort orderNotificationPort;

    @RabbitListener(queues = RabbitMQConstant.ORDER_TIMEOUT_PROCESS_QUEUE)
    public void handleOrderTimeout(Object payload) {
        OrderTimeoutMessage message = OrderEventMessageAdapter.toTimeout(payload);
        if (message == null || message.getOrderNumber() == null || message.getOrderNumber().isEmpty()) {
            log.warn("收到空的订单超时消息，忽略处理");
            return;
        }

        orderService.cancelTimeoutOrder(message.getOrderNumber());
    }

    @RabbitListener(queues = RabbitMQConstant.ORDER_PAID_NOTIFY_QUEUE)
    public void handleOrderPaidNotify(Object wirePayload) {
        OrderPaidMessage message = OrderEventMessageAdapter.toPaid(wirePayload);
        if (message == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", 1);
        payload.put("orderId", message.getOrderId());
        payload.put("content", "订单号：" + message.getOrderNumber());
        orderNotificationPort.broadcast(JSON.toJSONString(payload));
        log.info("MQ 处理支付成功通知完成，orderNo={}, orderId={}", message.getOrderNumber(), message.getOrderId());
    }

    @RabbitListener(queues = RabbitMQConstant.ORDER_PAID_AUDIT_QUEUE)
    public void handleOrderPaidAudit(Object payload) {
        OrderPaidMessage message = OrderEventMessageAdapter.toPaid(payload);
        if (message == null) {
            return;
        }
        log.info("MQ 审计支付成功事件，orderNo={}, orderId={}, userId={}, amount={}",
                message.getOrderNumber(), message.getOrderId(), message.getUserId(), message.getAmount());
    }
}
