package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品位置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

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

    //创建时间
    private LocalDateTime createTime;

    //更新时间
    private LocalDateTime updateTime;
}
