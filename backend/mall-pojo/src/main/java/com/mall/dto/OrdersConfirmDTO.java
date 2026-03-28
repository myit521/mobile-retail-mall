package com.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class OrdersConfirmDTO implements Serializable {

    @NotNull(message = "订单ID不能为空")
    private Long id;
    //订单状态 1待付款2-待处理, 3-处理中, 4-待自提, 5-已完成, 6-已取消, 7-退款
    // Legacy status field. Mall mode semantics are 2-待处理, 3-处理中, 4-待自提, 5-已完成, 6-已取消, 7-退款.
    @NotNull(message = "订单状态不能为空")
    private Integer status;

}
