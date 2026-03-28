package com.mall.dto;

import com.mall.entity.OrderDetail;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrdersDTO implements Serializable {

    // Legacy order DTO kept for compatibility with old takeout-oriented flows.

    private Long id;

    //订单号
    private String number;

    //订单状态 1待付款2-待处理, 3-处理中, 4-待自提, 5-已完成, 6-已取消, 7-退款
    private Integer status;

    //下单用户id
    private Long userId;

    //下单时间
    private LocalDateTime orderTime;

    //结账时间
    private LocalDateTime checkoutTime;

    //支付方式 1微信，2支付宝
    private Integer payMethod;

    //实收金额
    private BigDecimal amount;

    //备注
    private String remark;

    //用户名
    private String userName;


    private List<OrderDetail> orderDetails;

}
