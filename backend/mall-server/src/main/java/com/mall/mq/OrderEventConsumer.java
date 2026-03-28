package com.mall.mq;

import com.alibaba.fastjson.JSON;
import com.mall.constant.RabbitMQConstant;
import com.mall.message.OrderPaidMessage;
import com.mall.message.OrderTimeoutMessage;
import com.mall.service.OrderService;
import com.mall.websocket.WebSocketServer;
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
    private OrderService orderService;
    @Autowired
    private WebSocketServer webSocketServer;

    @RabbitListener(queues = RabbitMQConstant.ORDER_TIMEOUT_PROCESS_QUEUE)
    public void handleOrderTimeout(OrderTimeoutMessage message) {
        if (message == null || message.getOrderNumber() == null || message.getOrderNumber().isEmpty()) {
            log.warn("收到空的订单超时消息，忽略处理");
            return;
        }

        orderService.cancelTimeoutOrder(message.getOrderNumber());
    }

    @RabbitListener(queues = RabbitMQConstant.ORDER_PAID_NOTIFY_QUEUE)
    public void handleOrderPaidNotify(OrderPaidMessage message) {
        if (message == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", 1);
        payload.put("orderId", message.getOrderId());
        payload.put("content", "订单号：" + message.getOrderNumber());
        webSocketServer.sendMessage(JSON.toJSONString(payload));
        log.info("MQ 处理支付成功通知完成，orderNo={}, orderId={}", message.getOrderNumber(), message.getOrderId());
    }

    @RabbitListener(queues = RabbitMQConstant.ORDER_PAID_AUDIT_QUEUE)
    public void handleOrderPaidAudit(OrderPaidMessage message) {
        if (message == null) {
            return;
        }
        log.info("MQ 审计支付成功事件，orderNo={}, orderId={}, userId={}, amount={}",
                message.getOrderNumber(), message.getOrderId(), message.getUserId(), message.getAmount());
    }
}
