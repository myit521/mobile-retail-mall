package com.mall.vo;

import com.mall.entity.ProductSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.mall.entity.PhoneModel;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO implements Serializable {

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
    //更新时间
    private LocalDateTime updateTime;
    //分类名称
    private String categoryName;
    //商品关联的规格
    private List<ProductSpec> specList = new ArrayList<>();

    //商品适配的手机型号
    private List<PhoneModel> phoneModelList = new ArrayList<>();

}
