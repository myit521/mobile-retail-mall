package com.sky.notification.internal.messaging;

import com.sky.order.api.event.OrderPaidMessage;
import com.sky.order.api.event.OrderTimeoutMessage;
import org.springframework.amqp.support.converter.MessageConversionException;

final class OrderEventMessageAdapter {

    private OrderEventMessageAdapter() {
    }

    static OrderTimeoutMessage toTimeout(Object payload) {
        if (payload == null || payload instanceof OrderTimeoutMessage) {
            return (OrderTimeoutMessage) payload;
        }
        if (payload instanceof com.sky.message.OrderTimeoutMessage) {
            com.sky.message.OrderTimeoutMessage legacy = (com.sky.message.OrderTimeoutMessage) payload;
            return new OrderTimeoutMessage(legacy.getOrderNumber());
        }
        throw unsupported(payload, OrderTimeoutMessage.class);
    }

    static OrderPaidMessage toPaid(Object payload) {
        if (payload == null || payload instanceof OrderPaidMessage) {
            return (OrderPaidMessage) payload;
        }
        if (payload instanceof com.sky.message.OrderPaidMessage) {
            com.sky.message.OrderPaidMessage legacy = (com.sky.message.OrderPaidMessage) payload;
            return new OrderPaidMessage(
                    legacy.getOrderId(),
                    legacy.getOrderNumber(),
                    legacy.getUserId(),
                    legacy.getAmount());
        }
        throw unsupported(payload, OrderPaidMessage.class);
    }

    private static MessageConversionException unsupported(Object payload, Class<?> targetType) {
        return new MessageConversionException(
                "Unsupported order event payload " + payload.getClass().getName()
                        + "; expected " + targetType.getName() + " or its legacy wire type");
    }
}
