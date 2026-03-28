package com.mall.dto;

import lombok.Data;

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
    private Integer actualStock;
}
