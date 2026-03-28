package com.mall.mq;

import com.mall.constant.RabbitMQConstant;
import com.mall.message.OrderPaidMessage;
import com.mall.message.OrderTimeoutMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderEventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publishOrderTimeout(String orderNumber) {
        OrderTimeoutMessage message = OrderTimeoutMessage.builder()
                .orderNumber(orderNumber)
                .build();
        rabbitTemplate.convertAndSend(
                RabbitMQConstant.ORDER_EVENT_EXCHANGE,
                RabbitMQConstant.ORDER_TIMEOUT_DELAY_ROUTING_KEY,
                message
        );
        log.info("发送订单超时延迟消息，orderNo={}", orderNumber);
    }

    public void publishOrderPaid(OrderPaidMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMQConstant.ORDER_EVENT_EXCHANGE,
                RabbitMQConstant.ORDER_PAID_ROUTING_KEY,
                message
        );
        log.info("发送订单支付成功事件，orderNo={}, orderId={}", message.getOrderNumber(), message.getOrderId());
    }
}
