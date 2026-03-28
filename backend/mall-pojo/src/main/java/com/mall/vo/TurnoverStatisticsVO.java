package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 营业额统计VO - 用于ECharts图表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnoverStatisticsVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 日期列表（用于图表X轴）
     */
    private List<String> dateList;
    
    /**
     * 营业额列表（用于图表数据）
     */
    private List<BigDecimal> turnoverList;
    
    /**
     * 总营业额
     */
    private BigDecimal totalTurnover;
}
