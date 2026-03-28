package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 低库存预警
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlert implements Serializable {

    // 预警状态常量
    public static final Integer STATUS_PENDING = 0;   // 未处理
    public static final Integer STATUS_HANDLED = 1;   // 已处理
    public static final Integer STATUS_IGNORED = 2;   // 已忽略

    private static final long serialVersionUID = 1L;

    private Long id;

    //商品ID
    private Long productId;

    //商品名称（快照）
    private String productName;

    //触发时库存
    private Integer currentStock;

    //预警阈值
    private Integer alertThreshold;

    //状态：0未处理 1已处理 2已忽略
    private Integer alertStatus;

    //预警时间
    private LocalDateTime alertTime;

    //处理时间
    private LocalDateTime handleTime;

    //处理人ID
    private Long handleUser;
}
