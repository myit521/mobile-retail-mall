package com.sky.payment.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Verified callback fields only; amounts are integer minor currency units. */
@Getter
@RequiredArgsConstructor
public final class PaymentCallbackCommand {
    private final String orderNumber;
    private final String transactionId;
    private final Integer total;
}
