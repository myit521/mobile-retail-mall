package com.sky.config;

import com.sky.constant.RabbitMQConstant;
import com.sky.messaging.consumer.DeadLetterPublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.aopalliance.aop.Advice;

@Configuration
public class RabbitMQConfig {

    private static final int ORDER_TIMEOUT_MILLISECONDS = 15 * 60 * 1000;

    @Bean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public DirectExchange orderEventExchange() {
        return new DirectExchange(RabbitMQConstant.ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderEventDeadLetterExchange() {
        return new DirectExchange(RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderEventDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding orderEventDeadLetterBinding() {
        return BindingBuilder.bind(orderEventDeadLetterQueue()).to(orderEventDeadLetterExchange())
                .with(RabbitMQConstant.ORDER_EVENT_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public Advice idempotentConsumerRetryInterceptor(
            DeadLetterPublisher deadLetterPublisher,
            @Value("${sky.messaging.consumer.max-attempts:3}") int maxAttempts) {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .recoverer((message, cause) -> deadLetterPublisher.publish(message, cause, maxAttempts))
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory idempotentRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("idempotentConsumerRetryInterceptor") Advice retryInterceptor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(retryInterceptor);
        return factory;
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
