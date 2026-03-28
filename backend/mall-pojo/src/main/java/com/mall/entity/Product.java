package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //商品名称
    private String name;

    //品牌
    private String brand;

    //型号
    private String model;

    //商品分类id
    private Long categoryId;

    //商品价格
    private BigDecimal price;

    //库存
    private Integer stock;

    //图片
    private String image;

    //描述信息
    private String description;

    //规格参数JSON
    private String specs;

    //0 停售 1 起售
    private Integer status;

    //低库存预警阈值
    private Integer alertThreshold;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;

}
