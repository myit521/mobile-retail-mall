package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 订单统计报表VO - 用于ECharts图表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsReportVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 日期列表（用于图表X轴）
     */
    private List<String> dateList;
    
    /**
     * 每日订单数列表
     */
    private List<Integer> orderCountList;
    
    /**
     * 每日有效订单数列表（已完成）
     */
    private List<Integer> validOrderCountList;
    
    /**
     * 订单总数
     */
    private Integer totalOrderCount;
    
    /**
     * 有效订单数
     */
    private Integer validOrderCount;
    
    /**
     * 订单完成率
     */
    private Double completionRate;
}
