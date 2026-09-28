package com.sky.notification.internal.messaging;

import com.sky.notification.api.OrderNotificationPort;
import com.sky.messaging.consumer.MessageConsumptionService;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.api.event.OrderPaidMessage;
import com.sky.order.api.event.OrderTimeoutMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.amqp.rabbit.listener.adapter.HandlerAdapter;
import org.springframework.amqp.rabbit.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class OrderEventConsumerCompatibilityTest {

    @Test
    void liveListenerBoundaryAcceptsJsonBytesAndTextForAllThreeListeners() throws Exception {
        OrderApplicationService orders = mock(OrderApplicationService.class);
        RecordingNotificationPort notifications = new RecordingNotificationPort();
        OrderEventConsumer consumer = consumer(orders, notifications);
        String paid = "{\"eventId\":\"paid-1\",\"correlationId\":\"paid-1\",\"eventType\":\"ORDER_PAID\","
                + "\"payloadVersion\":1,\"aggregateId\":74,\"payload\":{\"orderId\":74,"
                + "\"orderNumber\":\"ORD-74\",\"userId\":92,\"amount\":56.78}}";
        String timeout = "{\"eventId\":\"timeout-1\",\"correlationId\":\"timeout-1\",\"eventType\":\"ORDER_TIMEOUT\","
                + "\"payloadVersion\":1,\"aggregateId\":74,\"payload\":{\"orderNumber\":\"ORD-74\"}}";
        for (Object payload : List.of(paid, paid.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            invokeListener(consumer, "handleOrderPaidNotify", wire(payload));
            invokeListener(consumer, "handleOrderPaidAudit", wire(payload));
        }
        for (Object payload : List.of(timeout, timeout.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            invokeListener(consumer, "handleOrderTimeout", wire(payload));
        }
        assertEquals(List.of("{\"orderId\":74,\"type\":1,\"content\":\"订单号：ORD-74\"}",
                "{\"orderId\":74,\"type\":1,\"content\":\"订单号：ORD-74\"}"), notifications.broadcasts);
        verify(orders, org.mockito.Mockito.times(2)).cancelTimeoutOrder("ORD-74");
        MessageConsumptionService consumption = (MessageConsumptionService)
                ReflectionTestUtils.getField(consumer, "consumptionService");
        verify(consumption, org.mockito.Mockito.times(2)).consume(org.mockito.ArgumentMatchers.eq("order-paid-audit"),
                org.mockito.ArgumentMatchers.eq("paid-1"), org.mockito.ArgumentMatchers.eq("ORD-74"), any(Runnable.class));
    }

    @Test
    void liveListenerBoundaryPreservesStoredLegacySerializedMessages() throws Exception {
        OrderApplicationService orders = mock(OrderApplicationService.class);
        RecordingNotificationPort notifications = new RecordingNotificationPort();
        OrderEventConsumer consumer = consumer(orders, notifications);
        invokeListener(consumer, "handleOrderTimeout", legacyWire(LEGACY_TIMEOUT));
        invokeListener(consumer, "handleOrderPaidNotify", legacyWire(LEGACY_PAID));
        invokeListener(consumer, "handleOrderPaidAudit", legacyWire(LEGACY_PAID));
        verify(orders).cancelTimeoutOrder("ORD-LEGACY-001");
        assertEquals(List.of("{\"orderId\":73,\"type\":1,\"content\":\"订单号：ORD-LEGACY-002\"}"), notifications.broadcasts);
    }

    @Test
    void liveListenerBoundaryStillRejectsUnsupportedTypesAndUnsafeEnvelopeFields() {
        OrderEventConsumer consumer = consumer(mock(OrderApplicationService.class), new RecordingNotificationPort());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invokeListener(consumer, "handleOrderPaidNotify", wire(73L)))
                .hasRootCauseInstanceOf(MessageConversionException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invokeListener(consumer, "handleOrderTimeout", wire("unexpected")))
                .hasRootCauseInstanceOf(MessageConversionException.class);
        String unsafe = "{\"eventId\":\"unsafe\",\"correlationId\":\"unsafe\",\"eventType\":\"ORDER_TIMEOUT\","
                + "\"payloadVersion\":1,\"aggregateId\":74,\"payload\":{\"orderNumber\":\"ORD-1\\nFORGED\"}}";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invokeListener(consumer, "handleOrderTimeout", wire(unsafe)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static void invokeListener(OrderEventConsumer consumer, String methodName, Message message) throws Exception {
        java.lang.reflect.Method method = OrderEventConsumer.class.getMethod(methodName, Object.class);
        DefaultMessageHandlerMethodFactory methods = new DefaultMessageHandlerMethodFactory();
        methods.afterPropertiesSet();
        MessagingMessageListenerAdapter listener = new MessagingMessageListenerAdapter(consumer, method);
        listener.setHandlerAdapter(new HandlerAdapter(methods.createInvocableHandlerMethod(consumer, method)));
        listener.onMessage(message, null);
    }

    private static Message wire(Object payload) {
        return new SimpleMessageConverter().toMessage(payload, new MessageProperties());
    }

    private static Message legacyWire(String fixture) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT);
        return new Message(Base64.getDecoder().decode(fixture), properties);
    }

    private static final String LEGACY_TIMEOUT =
            "rO0ABXNyACNjb20uc2t5Lm1lc3NhZ2UuT3JkZXJUaW1lb3V0TWVzc2FnZSIbBfqMp1oAAgABTAALb3JkZXJOdW1iZXJ0ABJMamF2YS9sYW5nL1N0cmluZzt4cHQADk9SRC1MRUdBQ1ktMDAx";
    private static final String LEGACY_PAID =
            "rO0ABXNyACBjb20uc2t5Lm1lc3NhZ2UuT3JkZXJQYWlkTWVzc2FnZVLnYoaUJdEjAgAETAAGYW1vdW50dAAWTGphdmEvbWF0aC9CaWdEZWNpbWFsO0wAB29yZGVySWR0ABBMamF2YS9sYW5nL0xvbmc7TAALb3JkZXJOdW1iZXJ0ABJMamF2YS9sYW5nL1N0cmluZztMAAZ1c2VySWRxAH4AAnhwc3IAFGphdmEubWF0aC5CaWdEZWNpbWFsVMcVV/mBKE8DAAJJAAVzY2FsZUwABmludFZhbHQAFkxqYXZhL21hdGgvQmlnSW50ZWdlcjt4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHAAAAACc3IAFGphdmEubWF0aC5CaWdJbnRlZ2VyjPyfH6k7+x0DAAZJAAhiaXRDb3VudEkACWJpdExlbmd0aEkAE2ZpcnN0Tm9uemVyb0J5dGVOdW1JAAxsb3dlc3RTZXRCaXRJAAZzaWdudW1bAAltYWduaXR1ZGV0AAJbQnhxAH4AB////////////////v////4AAAABdXIAAltCrPMX+AYIVOACAAB4cAAAAAIE0nh4c3IADmphdmEubGFuZy5Mb25nO4vkkMyPI98CAAFKAAV2YWx1ZXhxAH4ABwAAAAAAAABJdAAOT1JELUxFR0FDWS0wMDJzcQB+AA4AAAAAAAAAWw==";

    @Test
    void handlesLegacyTimeoutBytesUsingTheOriginalClassDescriptor() {
        OrderApplicationService orderService = mock(OrderApplicationService.class);
        OrderEventConsumer consumer = consumer(orderService, new RecordingNotificationPort());

        consumer.handleOrderTimeout(deserialize(LEGACY_TIMEOUT));

        verify(orderService).cancelTimeoutOrder("ORD-LEGACY-001");
    }

    @Test
    void handlesLegacyPaidBytesUsingTheOriginalClassDescriptor() {
        RecordingNotificationPort notifications = new RecordingNotificationPort();
        OrderEventConsumer consumer = consumer(mock(OrderApplicationService.class), notifications);

        consumer.handleOrderPaidNotify(deserialize(LEGACY_PAID));

        assertEquals(List.of("{\"orderId\":73,\"type\":1,\"content\":\"订单号：ORD-LEGACY-002\"}"),
                notifications.broadcasts);
    }

    @Test
    void rejectsPayloadTypesOutsideTheTwoCurrentAndTwoLegacyEvents() {
        OrderEventConsumer consumer = consumer(mock(OrderApplicationService.class), new RecordingNotificationPort());

        assertThrows(MessageConversionException.class, () -> consumer.handleOrderTimeout("unexpected"));
        assertThrows(MessageConversionException.class, () -> consumer.handleOrderPaidNotify(73L));
    }

    @Test
    void rejectsControlCharactersInLoggedOrderNumber() {
        OrderEventConsumer consumer = consumer(mock(OrderApplicationService.class), new RecordingNotificationPort());
        String envelope = "{\"eventId\":\"event-1\",\"correlationId\":\"correlation-1\","
                + "\"eventType\":\"ORDER_PAID\",\"payloadVersion\":1,\"aggregateId\":74,"
                + "\"payload\":{\"orderId\":74,\"orderNumber\":\"ORD-1\\nFORGED\","
                + "\"userId\":92,\"amount\":56.78}}";

        assertThrows(IllegalArgumentException.class,
                () -> consumer.handleOrderPaidNotify(envelope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void continuesToHandleCurrentEventTypes() throws Exception {
        OrderApplicationService orderService = mock(OrderApplicationService.class);
        RecordingNotificationPort notifications = new RecordingNotificationPort();
        OrderEventConsumer consumer = consumer(orderService, notifications);

        invokeListener(consumer, "handleOrderTimeout", wire(new OrderTimeoutMessage("ORD-CURRENT-001")));
        invokeListener(consumer, "handleOrderPaidNotify", wire(new OrderPaidMessage(
                74L, "ORD-CURRENT-002", 92L, new BigDecimal("56.78"))));

        verify(orderService).cancelTimeoutOrder("ORD-CURRENT-001");
        assertEquals(List.of("{\"orderId\":74,\"type\":1,\"content\":\"订单号：ORD-CURRENT-002\"}"),
                notifications.broadcasts);
        assertEquals(null, MDC.getCopyOfContextMap());
    }

    private static Object deserialize(String fixture) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT);
        Message message = new Message(Base64.getDecoder().decode(fixture), properties);
        return new SimpleMessageConverter().fromMessage(message);
    }

    private static OrderEventConsumer consumer(OrderApplicationService orderService,
                                               OrderNotificationPort notificationPort) {
        OrderEventConsumer consumer = new OrderEventConsumer();
        ReflectionTestUtils.setField(consumer, "orderService", orderService);
        ReflectionTestUtils.setField(consumer, "orderNotificationPort", notificationPort);
        MessageConsumptionService consumption = mock(MessageConsumptionService.class);
        when(consumption.consume(anyString(), anyString(), anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    assertEquals(invocation.getArgument(1), MDC.get("eventId"));
                    assertEquals(invocation.getArgument(1), MDC.get("correlationId"));
                    invocation.<Runnable>getArgument(3).run();
                    return true;
                });
        ReflectionTestUtils.setField(consumer, "consumptionService", consumption);
        return consumer;
    }

    private static final class RecordingNotificationPort implements OrderNotificationPort {
        private final List<String> broadcasts = new ArrayList<>();

        @Override
        public void broadcast(String message) {
            broadcasts.add(message);
        }

        @Override
        public void sendTo(String message, String clientId) {
        }
    }
}
