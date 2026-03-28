package com.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.DecimalMin;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrdersSubmitDTO implements Serializable {
    //付款方式
    @NotNull(message = "付款方式不能为空")
    private Integer payMethod;
    //备注
    private String remark;
    //总金额
    @NotNull(message = "总金额不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "总金额必须大于0")
    private BigDecimal amount;
}
