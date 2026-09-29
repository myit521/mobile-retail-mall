package com.sky.payment.internal;

import com.alibaba.fastjson.JSON;
import com.sky.entity.PaymentCallbackLog;
import com.sky.order.api.OrderApplicationService;
import com.sky.order.api.event.OrderPaidMessage;
import com.sky.observability.MdcContext;
import com.sky.observability.BusinessMetrics;
import com.sky.observability.SafeLogIdentifier;
import com.sky.payment.api.PaymentCallbackCommand;
import com.sky.payment.api.PaymentCallbackResult;
import com.sky.payment.internal.persistence.PaymentCallbackLogMapper;
import com.sky.payment.internal.persistence.PaymentEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackService {
    private final PaymentCallbackLogMapper callbacks;
    private final OrderApplicationService orders;
    private final PaymentEventMapper events;
    @Autowired(required = false)
    private BusinessMetrics metrics;

    @Transactional(rollbackFor = Exception.class)
    public PaymentCallbackResult handle(PaymentCallbackCommand command) {
        if (command == null || !StringUtils.hasText(command.getOrderNumber())
                || command.getOrderNumber().length() > 50 || !StringUtils.hasText(command.getTransactionId())
                || command.getTransactionId().length() > 64 || command.getTotal() == null || command.getTotal() < 0) {
            throw new IllegalArgumentException("Invalid verified payment fields");
        }
        SafeLogIdentifier.require(command.getOrderNumber(), "orderNumber", 50);
        SafeLogIdentifier.require(command.getTransactionId(), "transactionId", 64);
        LocalDateTime now = LocalDateTime.now();
        PaymentCallbackLog callback = PaymentCallbackLog.builder()
                .outTradeNo(command.getOrderNumber()).transactionId(command.getTransactionId())
                .callbackType("PAY_SUCCESS").callbackStatus("SUCCESS").handleCount(1)
                .callbackTime(now).handleTime(now).createTime(now).updateTime(now).build();
        try {
            if (callbacks.insert(callback) != 1) {
                throw new IllegalStateException("Payment callback key was not persisted");
            }
        } catch (DuplicateKeyException duplicate) {
            // Shared current read sees the commit without upgrading duplicate insert shared locks.
            PaymentCallbackLog existing = callbacks.getByTransactionIdForShare(command.getTransactionId());
            if (existing == null || !command.getOrderNumber().equals(existing.getOutTradeNo())
                    || !"SUCCESS".equals(existing.getCallbackStatus())) {
                throw new IllegalStateException("Payment transaction is already bound or incomplete");
            }
            if (metrics != null) metrics.duplicateCallback();
            return PaymentCallbackResult.DUPLICATE;
        }
        OrderPaidMessage paid = orders.completeVerifiedPayment(command.getOrderNumber(), BigDecimal.valueOf(command.getTotal(), 2));
        if (paid == null) {
            throw new IllegalStateException("Order was already paid by another transaction");
        }
        String eventId = UUID.randomUUID().toString();
        if (events.insertPending(eventId, command.getTransactionId(), paid.getOrderId(),
                "ORDER_PAID:" + paid.getOrderId(), JSON.toJSONString(paid)) != 1) {
            throw new IllegalStateException("Payment event was not persisted");
        }
        try (MdcContext ignored = MdcContext.open(Map.of("eventId", eventId,
                "correlationId", command.getTransactionId(), "orderId", paid.getOrderId()))) {
            log.info("Verified payment persisted with pending outbox event, orderNo={}", command.getOrderNumber());
        }
        return PaymentCallbackResult.APPLIED;
    }
}
