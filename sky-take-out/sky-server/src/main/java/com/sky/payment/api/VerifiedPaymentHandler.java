package com.sky.payment.api;

public interface VerifiedPaymentHandler {
    PaymentCallbackResult handle(PaymentCallbackCommand command);
}
