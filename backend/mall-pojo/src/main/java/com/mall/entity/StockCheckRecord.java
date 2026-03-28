package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 盘点记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckRecord implements Serializable {

    // 记录状态常量
    public static final Integer STATUS_PENDING = 0;    // 待确认
    public static final Integer STATUS_ADJUSTED = 1;   // 已调整
    public static final Integer STATUS_IGNORED = 2;    // 已忽略

    private static final long serialVersionUID = 1L;

    private Long id;

    //盘点计划ID
    private Long planId;

    //商品ID
    private Long productId;

    //系统账面库存
    private Integer systemStock;

    //实盘库存
    private Integer actualStock;

    //差异数量（actual - system）
    private Integer diffQuantity;

    //状态：0待确认 1已调整 2已忽略
    private Integer status;

    //盘点时间
    private LocalDateTime createTime;

    //调整时间
    private LocalDateTime updateTime;
}
