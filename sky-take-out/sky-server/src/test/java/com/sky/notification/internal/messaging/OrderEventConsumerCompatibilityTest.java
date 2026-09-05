package com.sky.notification.internal.messaging;

import com.sky.notification.api.OrderNotificationPort;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.api.event.OrderPaidMessage;
import com.sky.order.api.event.OrderTimeoutMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventConsumerCompatibilityTest {

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
    void continuesToHandleCurrentEventTypes() {
        OrderApplicationService orderService = mock(OrderApplicationService.class);
        RecordingNotificationPort notifications = new RecordingNotificationPort();
        OrderEventConsumer consumer = consumer(orderService, notifications);

        consumer.handleOrderTimeout(new OrderTimeoutMessage("ORD-CURRENT-001"));
        consumer.handleOrderPaidNotify(new OrderPaidMessage(
                74L, "ORD-CURRENT-002", 92L, new BigDecimal("56.78")));

        verify(orderService).cancelTimeoutOrder("ORD-CURRENT-001");
        assertEquals(List.of("{\"orderId\":74,\"type\":1,\"content\":\"订单号：ORD-CURRENT-002\"}"),
                notifications.broadcasts);
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
