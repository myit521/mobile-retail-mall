package com.mall.config;

import com.mall.constant.RabbitMQConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private static final int ORDER_TIMEOUT_MILLISECONDS = 15 * 60 * 1000;

    @Bean
    public DirectExchange orderEventExchange() {
        return new DirectExchange(RabbitMQConstant.ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderTimeoutDelayQueue() {
        return QueueBuilder.durable(RabbitMQConstant.ORDER_TIMEOUT_DELAY_QUEUE)
                .deadLetterExchange(RabbitMQConstant.ORDER_EVENT_EXCHANGE)
                .deadLetterRoutingKey(RabbitMQConstant.ORDER_TIMEOUT_PROCESS_ROUTING_KEY)
                .ttl(ORDER_TIMEOUT_MILLISECONDS)
                .build();
    }

    @Bean
    public Queue orderTimeoutProcessQueue() {
        return QueueBuilder.durable(RabbitMQConstant.ORDER_TIMEOUT_PROCESS_QUEUE).build();
    }

    @Bean
    public Queue orderPaidNotifyQueue() {
        return QueueBuilder.durable(RabbitMQConstant.ORDER_PAID_NOTIFY_QUEUE).build();
    }

    @Bean
    public Queue orderPaidAuditQueue() {
        return QueueBuilder.durable(RabbitMQConstant.ORDER_PAID_AUDIT_QUEUE).build();
    }

    @Bean
    public Binding orderTimeoutDelayBinding() {
        return BindingBuilder.bind(orderTimeoutDelayQueue())
                .to(orderEventExchange())
                .with(RabbitMQConstant.ORDER_TIMEOUT_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderTimeoutProcessBinding() {
        return BindingBuilder.bind(orderTimeoutProcessQueue())
                .to(orderEventExchange())
                .with(RabbitMQConstant.ORDER_TIMEOUT_PROCESS_ROUTING_KEY);
    }

    @Bean
    public Binding orderPaidNotifyBinding() {
        return BindingBuilder.bind(orderPaidNotifyQueue())
                .to(orderEventExchange())
                .with(RabbitMQConstant.ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    public Binding orderPaidAuditBinding() {
        return BindingBuilder.bind(orderPaidAuditQueue())
                .to(orderEventExchange())
                .with(RabbitMQConstant.ORDER_PAID_ROUTING_KEY);
    }
}
