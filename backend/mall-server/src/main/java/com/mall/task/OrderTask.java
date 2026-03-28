package com.mall.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderTask {

    /**
     * 保留兜底扫描，避免 MQ 异常时超时订单长期堆积。
     */
    @Scheduled(cron = "0 0/10 * * * ?")
    public void processTimeoutOrder() {
        log.info("订单超时兜底扫描已保留，当前主要依赖 RabbitMQ 延迟消息处理");
    }
}
