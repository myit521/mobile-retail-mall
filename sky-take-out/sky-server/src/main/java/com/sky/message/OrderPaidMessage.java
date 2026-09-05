package com.sky.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Wire-only compatibility shell for paid messages serialized before the module migration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidMessage implements Serializable {
    private static final long serialVersionUID = 5973851760880898339L;

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
}
