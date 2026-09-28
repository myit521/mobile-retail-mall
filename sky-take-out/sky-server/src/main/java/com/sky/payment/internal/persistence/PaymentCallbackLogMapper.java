package com.sky.payment.internal.persistence;

import com.sky.entity.PaymentCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付回调日志 Mapper 接口
 */
@Mapper
public interface PaymentCallbackLogMapper {
    @Select("select * from payment_callback_log where transaction_id = #{transactionId} lock in share mode")
    PaymentCallbackLog getByTransactionIdForShare(String transactionId);
    
    /**
     * 插入回调日志
     * @param log 回调日志信息
     * @return 影响行数
     */
    int insert(PaymentCallbackLog log);
    
    /**
     * 根据商户订单号和交易 ID 查询回调日志
     * @param outTradeNo 商户订单号
     * @param transactionId 微信支付交易号
     * @return 回调日志
     */
    PaymentCallbackLog getByOutTradeNoAndTransactionId(@Param("outTradeNo") String outTradeNo, 
                                                        @Param("transactionId") String transactionId);
    
    /**
     * 根据商户订单号查询最新的回调日志
     * @param outTradeNo 商户订单号
     * @return 回调日志
     */
    PaymentCallbackLog getLatestByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    /**
     * 更新回调日志
     * @param log 回调日志信息
     * @return 影响行数
     */
    int update(PaymentCallbackLog log);
}
