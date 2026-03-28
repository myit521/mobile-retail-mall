package com.mall.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PhoneModelPageQueryDTO implements Serializable {

    private Integer page;

    private Integer pageSize;

    //手机品牌
    private String brand;

    //手机型号名称
    private String modelName;

    //状态 0:禁用 1:启用
    private Integer status;

}
