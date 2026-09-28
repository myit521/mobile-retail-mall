package com.sky.notification.internal.websocket;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "sky.websocket")
@Data
public class WebSocketTicketProperties {
    private Duration ticketTtl = Duration.ofSeconds(30);
    private String ticketKeyPrefix = "websocket:ticket:";
}
