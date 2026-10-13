package com.sky.payment.internal.refund;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RefundSettlementService {
    private final RefundRequestMapper refunds;

    @Transactional
    public void recordResult(String refundNo, String orderNumber, BigDecimal amount, String status) {
        RefundRequest refund = refunds.lock(refundNo);
        if (refund == null || !refund.getOrderNumber().equals(orderNumber)
                || amount == null || refund.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Refund result does not match persisted request");
        }
        if ("SUCCESS".equals(refund.getStatus())) return;
        if ("SUCCESS".equals(status)) {
            if (refunds.markOrderRefunded(refund.getOrderId()) != 1) {
                throw new IllegalStateException("Paid order was not marked refunded");
            }
        } else if (!"PROCESSING".equals(status) && !"CLOSED".equals(status) && !"ABNORMAL".equals(status)) {
            throw new IllegalArgumentException("Unknown refund status");
        }
        refunds.updateStatus(refundNo, status);
    }
}
