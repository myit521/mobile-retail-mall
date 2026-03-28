package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 数据概览VO - 用于仪表盘
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverviewVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 今日营业额
     */
    private BigDecimal todayTurnover;
    
    /**
     * 今日订单数
     */
    private Integer todayOrderCount;
    
    /**
     * 今日有效订单数（已完成）
     */
    private Integer todayValidOrderCount;
    
    /**
     * 待处理订单数
     */
    private Integer waitingOrderCount;
    
    /**
     * 处理中订单数
     */
    private Integer processingOrderCount;
    
    /**
     * 商品总数
     */
    private Integer productCount;
    
    /**
     * 在售商品数
     */
    private Integer onSaleProductCount;
    
    /**
     * 库存预警商品数
     */
    private Integer lowStockProductCount;
}
