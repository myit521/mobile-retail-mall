package com.mall.service;

import com.mall.dto.*;
import com.mall.result.PageResult;
import com.mall.result.Result;
import com.mall.vo.OrderPaymentVO;
import com.mall.vo.OrderStatisticsVO;
import com.mall.vo.OrderSubmitVO;
import com.mall.vo.OrderVO;

public interface OrderService {
    OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO);

    PageResult pageQuery4User(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderVO show(Long id);

    Result userCancel(Long id);

    Result repetition(Long id);

    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO statistics();

    String confirm(OrdersConfirmDTO ordersConfirmDTO);

    String rejection(OrdersRejectionDTO ordersRejectionDTO);

    String adminCancel(OrdersCancelDTO ordersCancelDTO);


    
    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 支付成功，修改订单状态（增强版，带校验）
     * @param outTradeNo 商户订单号
     * @param transactionId 微信支付交易号
     * @param actualAmount 实际支付金额（单位：元）
     */
    void paySuccessWithValidation(String outTradeNo, String transactionId, Double actualAmount);

    /**
     * MQ 处理订单超时取消
     * @param orderNumber 商户订单号
     */
    void cancelTimeoutOrder(String orderNumber);
}
