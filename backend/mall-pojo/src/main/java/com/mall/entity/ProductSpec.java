package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品规格
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    //商品id
    private Long productId;

    //规格名称（如颜色、内存）
    private String specName;

    //规格值（如黑色、128GB）
    private String specValue;

    //规格价格（空则用商品价格）
    private BigDecimal specPrice;

    //规格库存
    private Integer specStock;

}
