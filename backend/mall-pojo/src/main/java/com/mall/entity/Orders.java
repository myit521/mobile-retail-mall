package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单
 * 
 *
 * 1-待支付：用户未支付
 * 2-待处理：已支付，商家未接单
 * 3-处理中：商家已接单，制作中
 * 4-待自提：已制作完成，等待顾客取货
 * 5-已完成：顾客已取货/订单完成
 * 6-已取消：订单被取消
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Orders implements Serializable {

    /**
     * 订单状态：
     * 1-待支付
     * 2-待处理
     * 3-处理中
     * 4-待自提
     * 5-已完成
     * 6-已取消
     */
    public static final Integer PENDING_PAYMENT = 1;
    public static final Integer PENDING_PROCESS = 2;
    public static final Integer PROCESSING = 3;
    public static final Integer READY_FOR_PICKUP = 4;
    public static final Integer COMPLETED = 5;
    public static final Integer CANCELLED = 6;

    // Legacy takeout-oriented aliases kept to avoid breaking older code paths.
    public static final Integer TO_BE_CONFIRMED = PENDING_PROCESS;
    public static final Integer CONFIRMED = PROCESSING;
    public static final Integer DELIVERY_IN_PROGRESS = READY_FOR_PICKUP;

    /**
     * 支付状态 0未支付 1已支付 2退款
     */
    public static final Integer UN_PAID = 0;
    public static final Integer PAID = 1;
    public static final Integer REFUND = 2;

    private static final long serialVersionUID = 1L;

    private Long id;

    //订单号
    private String number;

    //订单状态 1待支付 2待处理 3处理中 4待自提 5已完成 6已取消
    private Integer status;

    //下单用户id
    private Long userId;



    //下单时间
    private LocalDateTime orderTime;

    //结账时间
    private LocalDateTime checkoutTime;

    //支付方式 1微信，2支付宝
    private Integer payMethod;

    //支付状态 0未支付 1已支付 2退款
    private Integer payStatus;

    //实收金额
    private BigDecimal amount;

    //备注
    private String remark;

    //用户名
    private String userName;

    //订单取消原因
    private String cancelReason;

    //订单拒绝原因
    private String rejectionReason;

    //订单取消时间
    private LocalDateTime cancelTime;



}
