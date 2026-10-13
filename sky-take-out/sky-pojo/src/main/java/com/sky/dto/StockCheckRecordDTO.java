package com.sky.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 提交盘点记录
 */
@Data
public class StockCheckRecordDTO implements Serializable {

    //盘点计划ID
    private Long planId;

    //商品ID
    private Long productId;

    //实盘库存
    @NotNull(message = "实盘库存不能为空")
    @Min(value = 0, message = "实盘库存不能为负数")
    private Integer actualStock;
}
