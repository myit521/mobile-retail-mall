package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 商品销量统计VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSalesVO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 商品ID
     */
    private Long productId;
    
    /**
     * 商品名称
     */
    private String productName;
    
    /**
     * 商品图片
     */
    private String image;
    
    /**
     * 销量
     */
    private Integer salesCount;
    
    /**
     * 销售额
     */
    private java.math.BigDecimal salesAmount;
}
