package com.mall.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidMessage implements Serializable {
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
}
