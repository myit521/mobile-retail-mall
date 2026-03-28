package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品位置展示（包含商品名称）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLocationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //商品ID
    private Long productId;

    //商品名称
    private String productName;

    //商品图片
    private String productImage;

    //货架编号
    private String shelfCode;

    //层数
    private Integer layerNum;

    //完整位置编码
    private String positionCode;

    //备注说明
    private String remark;
}
