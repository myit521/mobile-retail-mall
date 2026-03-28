package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品与手机型号关联表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPhoneModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //商品id
    private Long productId;

    //手机型号id
    private Long phoneModelId;

}
