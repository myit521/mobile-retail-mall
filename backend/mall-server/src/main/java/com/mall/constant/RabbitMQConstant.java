package com.mall.constant;

public final class RabbitMQConstant {

    private RabbitMQConstant() {
    }

    public static final String ORDER_EVENT_EXCHANGE = "order.event.exchange";

    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "order.timeout.delay.queue";
    public static final String ORDER_TIMEOUT_PROCESS_QUEUE = "order.timeout.process.queue";
    public static final String ORDER_PAID_NOTIFY_QUEUE = "order.paid.notify.queue";
    public static final String ORDER_PAID_AUDIT_QUEUE = "order.paid.audit.queue";

    public static final String ORDER_TIMEOUT_DELAY_ROUTING_KEY = "order.timeout.delay";
    public static final String ORDER_TIMEOUT_PROCESS_ROUTING_KEY = "order.timeout.process";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";
}
