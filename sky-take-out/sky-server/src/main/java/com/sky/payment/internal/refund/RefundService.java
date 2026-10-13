package com.sky.payment.internal.refund;

import com.alibaba.fastjson.JSONObject;
import com.sky.entity.Orders;
import com.sky.utils.WeChatPayUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService implements com.sky.payment.api.RefundApplicationService {
    private final RefundRequestMapper refunds;
    private final WeChatPayUtil wechat;
    private final RefundSettlementService settlement;

    public void recordIntent(Orders order) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Refund intent must be recorded in the order transaction");
        }
        String refundNo = "R" + order.getId();
        RefundRequest existing = refunds.find(refundNo);
        if (existing != null) {
            if (!order.getId().equals(existing.getOrderId())
                    || !order.getNumber().equals(existing.getOrderNumber())
                    || order.getAmount().compareTo(existing.getAmount()) != 0) {
                throw new IllegalStateException("Existing refund intent does not match order");
            }
            return;
        }
        if (refunds.insert(order.getId(), order.getNumber(), refundNo, order.getAmount()) != 1) {
            throw new IllegalStateException("Refund intent was not persisted");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dispatch(refundNo);
                } catch (Exception failure) {
                    log.error("Refund dispatch deferred for retry, orderId={}", order.getId(), failure);
                }
            }
        });
    }

    public void dispatch(String refundNo) throws Exception {
        RefundRequest request = refunds.find(refundNo);
        if (request == null || "SUCCESS".equals(request.getStatus())
                || "CLOSED".equals(request.getStatus()) || "ABNORMAL".equals(request.getStatus())) return;
        if ("PENDING".equals(request.getStatus())) {
            JSONObject response = JSONObject.parseObject(wechat.refund(request.getOrderNumber(), refundNo,
                    request.getAmount(), request.getAmount()));
            if (response == null || !refundNo.equals(response.getString("out_refund_no"))
                    || !request.getOrderNumber().equals(response.getString("out_trade_no"))
                    || !isKnownStatus(response.getString("status"))) {
                throw new IllegalStateException("Invalid refund application response");
            }
            refunds.markProcessing(refundNo);
            return;
        }
        JSONObject response = wechat.queryRefund(refundNo);
        if (response == null || !refundNo.equals(response.getString("out_refund_no"))
                || !request.getOrderNumber().equals(response.getString("out_trade_no"))) {
            throw new IllegalStateException("Invalid refund query response");
        }
        JSONObject amount = response.getJSONObject("amount");
        if (amount == null || amount.getInteger("refund") == null) {
            throw new IllegalStateException("Refund query is missing amount");
        }
        settlement.recordResult(refundNo, request.getOrderNumber(),
                BigDecimal.valueOf(amount.getLongValue("refund"), 2), response.getString("status"));
    }

    @Scheduled(fixedDelay = 60000)
    public void retryUnfinished() {
        for (RefundRequest request : refunds.due(LocalDateTime.now().minusMinutes(1))) {
            try {
                dispatch(request.getOutRefundNo());
            } catch (Exception failure) {
                log.error("Refund reconciliation deferred, orderId={}", request.getOrderId(), failure);
            }
        }
    }

    private boolean isKnownStatus(String status) {
        return "PROCESSING".equals(status) || "SUCCESS".equals(status)
                || "CLOSED".equals(status) || "ABNORMAL".equals(status);
    }
}
