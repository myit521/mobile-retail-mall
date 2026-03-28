package com.mall.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ProductPageQueryDTO implements Serializable {

    private Integer page;

    private Integer pageSize;

    private String name;

    //分类id
    private Integer categoryId;

    //状态 0表示禁用 1表示启用
    private Integer status;

    //手机型号ID（按型号查询商品）
    private Long phoneModelId;

}
