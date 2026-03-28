package com.mall.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 手动调整库存
 */
@Data
public class StockAdjustDTO implements Serializable {

    //商品ID
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    //调整数量（正数增加，负数减少）
    @NotNull(message = "调整数量不能为空")
    private Integer quantity;

    //备注
    @NotBlank(message = "备注不能为空")
    private String remark;
}
