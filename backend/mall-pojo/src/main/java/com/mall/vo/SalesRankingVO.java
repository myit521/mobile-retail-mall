package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 销量排行榜VO - 用于ECharts图表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesRankingVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 商品名称列表（用于图表X轴/图例）
     */
    private List<String> nameList;
    
    /**
     * 销量列表（用于图表数据）
     */
    private List<Integer> salesList;
    
    /**
     * 销售额列表
     */
    private List<java.math.BigDecimal> amountList;
    
    /**
     * 详细数据列表
     */
    private List<ProductSalesVO> details;
}
