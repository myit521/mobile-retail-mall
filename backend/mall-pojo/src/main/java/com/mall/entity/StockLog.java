package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存变动日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockLog implements Serializable {

    // 变动类型常量
    public static final Integer TYPE_IN = 1;       // 入库
    public static final Integer TYPE_OUT = 2;      // 出库(下单)
    public static final Integer TYPE_RETURN = 3;   // 归还(取消)
    public static final Integer TYPE_ADJUST = 4;   // 手动调整
    public static final Integer TYPE_CHECK = 5;    // 盘点

    private static final long serialVersionUID = 1L;

    private Long id;

    //商品ID
    private Long productId;

    //变动类型：1入库 2出库(下单) 3归还(取消) 4手动调整 5盘点
    private Integer changeType;

    //变动数量（正数增加，负数减少）
    private Integer changeQuantity;

    //变动前库存
    private Integer beforeStock;

    //变动后库存
    private Integer afterStock;

    //关联订单ID
    private Long orderId;

    //备注
    private String remark;

    //操作人ID（系统自动则为NULL）
    private Long operatorId;

    //记录时间
    private LocalDateTime createTime;
}
