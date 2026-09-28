package com.sky.notification.internal.messaging;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.messaging.consumer.OrderEventEnvelope;
import com.sky.order.api.event.OrderPaidMessage;
import com.sky.order.api.event.OrderTimeoutMessage;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

import java.nio.charset.StandardCharsets;

final class OrderEventMessageAdapter {
    private static final SimpleMessageConverter WIRE_CONVERTER = new SimpleMessageConverter();

    private OrderEventMessageAdapter() {
    }

    static AdaptedOrderEvent<OrderTimeoutMessage> toTimeout(Object payload) {
        if (payload instanceof Message) {
            payload = WIRE_CONVERTER.fromMessage((Message) payload);
        }
        OrderEventEnvelope envelope = jsonEnvelope(payload);
        if (envelope != null) {
            envelope.requireType("ORDER_TIMEOUT");
            String orderNumber = envelope.getPayload().getString("orderNumber");
            return new AdaptedOrderEvent<>(envelope, new OrderTimeoutMessage(orderNumber));
        }
        if (payload instanceof OrderTimeoutMessage) {
            OrderTimeoutMessage message = (OrderTimeoutMessage) payload;
            return legacyTimeout(message);
        }
        if (payload instanceof com.sky.message.OrderTimeoutMessage) {
            com.sky.message.OrderTimeoutMessage legacy = (com.sky.message.OrderTimeoutMessage) payload;
            return legacyTimeout(new OrderTimeoutMessage(legacy.getOrderNumber()));
        }
        throw unsupported(payload, OrderTimeoutMessage.class);
    }

    static AdaptedOrderEvent<OrderPaidMessage> toPaid(Object payload) {
        if (payload instanceof Message) {
            payload = WIRE_CONVERTER.fromMessage((Message) payload);
        }
        OrderEventEnvelope envelope = jsonEnvelope(payload);
        if (envelope != null) {
            envelope.requireType("ORDER_PAID");
            JSONObject body = envelope.getPayload();
            Long orderId = body.getLong("orderId");
            if (!envelope.getAggregateId().equals(orderId)) {
                throw new MessageConversionException("ORDER_PAID aggregateId does not match payload orderId");
            }
            return new AdaptedOrderEvent<>(envelope, new OrderPaidMessage(orderId,
                    body.getString("orderNumber"), body.getLong("userId"), body.getBigDecimal("amount")));
        }
        if (payload instanceof OrderPaidMessage) {
            return legacyPaid((OrderPaidMessage) payload);
        }
        if (payload instanceof com.sky.message.OrderPaidMessage) {
            com.sky.message.OrderPaidMessage legacy = (com.sky.message.OrderPaidMessage) payload;
            return legacyPaid(new OrderPaidMessage(
                    legacy.getOrderId(),
                    legacy.getOrderNumber(),
                    legacy.getUserId(),
                    legacy.getAmount()));
        }
        throw unsupported(payload, OrderPaidMessage.class);
    }

    private static AdaptedOrderEvent<OrderTimeoutMessage> legacyTimeout(OrderTimeoutMessage message) {
        JSONObject body = new JSONObject();
        body.put("orderNumber", message.getOrderNumber());
        OrderEventEnvelope envelope = OrderEventEnvelope.legacy("ORDER_TIMEOUT", message.getOrderNumber(), 0L, body);
        return new AdaptedOrderEvent<>(envelope, message);
    }

    private static AdaptedOrderEvent<OrderPaidMessage> legacyPaid(OrderPaidMessage message) {
        JSONObject body = (JSONObject) JSON.toJSON(message);
        OrderEventEnvelope envelope = OrderEventEnvelope.legacy("ORDER_PAID", message.getOrderNumber(), message.getOrderId(), body);
        return new AdaptedOrderEvent<>(envelope, message);
    }

    private static OrderEventEnvelope jsonEnvelope(Object payload) {
        String json = null;
        if (payload instanceof byte[]) {
            json = new String((byte[]) payload, StandardCharsets.UTF_8);
        } else if (payload instanceof String && ((String) payload).trim().startsWith("{")) {
            json = (String) payload;
        }
        if (json != null) {
            try {
                return OrderEventEnvelope.fromJson(json);
            } catch (IllegalArgumentException exception) {
                throw new MessageConversionException("Invalid order event envelope", exception);
            }
        }
        return null;
    }

    private static MessageConversionException unsupported(Object payload, Class<?> targetType) {
        return new MessageConversionException(
                "Unsupported order event payload " + (payload == null ? "null" : payload.getClass().getName())
                        + "; expected " + targetType.getName() + " or its legacy wire type");
    }

    static final class AdaptedOrderEvent<T> {
        private final OrderEventEnvelope envelope;
        private final T payload;

        AdaptedOrderEvent(OrderEventEnvelope envelope, T payload) {
            this.envelope = envelope;
            this.payload = payload;
        }

        OrderEventEnvelope getEnvelope() { return envelope; }
        T getPayload() { return payload; }
    }
}
