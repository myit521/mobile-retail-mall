package com.sky.payment.internal.refund;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RefundRequest {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private String outRefundNo;
    private BigDecimal amount;
    private String status;
    private LocalDateTime updatedAt;
}
