package com.mall.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 设置商品位置
 */
@Data
public class ProductLocationDTO implements Serializable {

    //商品ID
    private Long productId;

    //货架编号（如 A-01）
    private String shelfCode;

    //层数（从下往上）
    private Integer layerNum;

    //完整位置编码（如 A-01-3-05）
    private String positionCode;

    //备注说明
    private String remark;
}
