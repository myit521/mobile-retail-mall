package com.sky.payment.internal.application;

import com.sky.entity.PaymentCallbackLog;
import com.sky.payment.internal.persistence.PaymentCallbackLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Keeps payment callback persistence behind the payment application boundary.
 */
@Service
public class PaymentCallbackLogService {

    @Autowired
    private PaymentCallbackLogMapper paymentCallbackLogMapper;

    public PaymentCallbackLog getByOutTradeNoAndTransactionId(String outTradeNo, String transactionId) {
        return paymentCallbackLogMapper.getByOutTradeNoAndTransactionId(outTradeNo, transactionId);
    }

    public void insert(PaymentCallbackLog callbackLog) {
        paymentCallbackLogMapper.insert(callbackLog);
    }

    public void update(PaymentCallbackLog callbackLog) {
        paymentCallbackLogMapper.update(callbackLog);
    }
}
