package com.mall.dto;

import com.mall.entity.ProductSpec;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductDTO implements Serializable {

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
    //规格列表
    private List<ProductSpec> specList = new ArrayList<>();

    //适配手机型号ID列表
    private List<Long> phoneModelIds = new ArrayList<>();

}
