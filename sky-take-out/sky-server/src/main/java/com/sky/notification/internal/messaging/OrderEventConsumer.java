package com.sky.notification.internal.messaging;

import com.alibaba.fastjson.JSON;
import com.sky.constant.RabbitMQConstant;
import com.sky.messaging.consumer.MessageConsumptionService;
import com.sky.messaging.consumer.OrderEventEnvelope;
import com.sky.notification.api.OrderNotificationPort;
import com.sky.observability.MdcContext;
import com.sky.observability.SafeLogIdentifier;
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
    @Autowired
    private MessageConsumptionService consumptionService;

    @RabbitListener(queues = RabbitMQConstant.ORDER_TIMEOUT_PROCESS_QUEUE,
            containerFactory = "idempotentRabbitListenerContainerFactory")
    public void handleOrderTimeout(Object payload) {
        OrderEventMessageAdapter.AdaptedOrderEvent<OrderTimeoutMessage> event = OrderEventMessageAdapter.toTimeout(payload);
        try (MdcContext ignored = eventContext(event.getEnvelope())) {
            OrderTimeoutMessage message = event.getPayload();
            requireOrderNumber(message.getOrderNumber());
            consumptionService.consume("order-timeout", event.getEnvelope().getEventId(), message.getOrderNumber(),
                    () -> orderService.cancelTimeoutOrder(message.getOrderNumber()));
        }
    }

    @RabbitListener(queues = RabbitMQConstant.ORDER_PAID_NOTIFY_QUEUE,
            containerFactory = "idempotentRabbitListenerContainerFactory")
    public void handleOrderPaidNotify(Object wirePayload) {
        OrderEventMessageAdapter.AdaptedOrderEvent<OrderPaidMessage> event = OrderEventMessageAdapter.toPaid(wirePayload);
        try (MdcContext ignored = eventContext(event.getEnvelope())) {
            OrderPaidMessage message = event.getPayload();
            requirePaid(message);
            consumptionService.consume("order-paid-notify", event.getEnvelope().getEventId(), message.getOrderNumber(), () -> {
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", 1);
                payload.put("orderId", message.getOrderId());
                payload.put("content", "订单号：" + message.getOrderNumber());
                orderNotificationPort.broadcast(JSON.toJSONString(payload));
                log.info("MQ 处理支付成功通知完成，orderNo={}", message.getOrderNumber());
            });
        }
    }

    @RabbitListener(queues = RabbitMQConstant.ORDER_PAID_AUDIT_QUEUE,
            containerFactory = "idempotentRabbitListenerContainerFactory")
    public void handleOrderPaidAudit(Object payload) {
        OrderEventMessageAdapter.AdaptedOrderEvent<OrderPaidMessage> event = OrderEventMessageAdapter.toPaid(payload);
        try (MdcContext ignored = eventContext(event.getEnvelope())) {
            OrderPaidMessage message = event.getPayload();
            requirePaid(message);
            consumptionService.consume("order-paid-audit", event.getEnvelope().getEventId(), message.getOrderNumber(),
                    () -> log.info("MQ 审计支付成功事件，orderNo={}, amount={}",
                            message.getOrderNumber(), message.getAmount()));
        }
    }

    private MdcContext eventContext(OrderEventEnvelope envelope) {
        return MdcContext.open(Map.of("eventId", envelope.getEventId(),
                "correlationId", envelope.getCorrelationId(), "orderId", envelope.getAggregateId()));
    }

    private void requirePaid(OrderPaidMessage message) {
        requireOrderNumber(message.getOrderNumber());
        if (message.getOrderId() == null || message.getUserId() == null || message.getAmount() == null) {
            throw new IllegalArgumentException("Incomplete ORDER_PAID payload");
        }
    }

    private void requireOrderNumber(String orderNumber) {
        SafeLogIdentifier.require(orderNumber, "orderNumber", 50);
    }
}
