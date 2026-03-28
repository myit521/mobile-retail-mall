package com.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class OrdersRejectionDTO implements Serializable {

    @NotNull(message = "订单ID不能为空")
    private Long id;

    //订单拒绝原因
    @NotBlank(message = "拒绝原因不能为空")
    private String rejectionReason;

}
