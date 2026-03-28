package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 手机型号
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //手机品牌（如Apple、Samsung、Xiaomi）
    private String brand;

    //手机型号名称（如iPhone 15 Pro、Galaxy S24）
    private String modelName;

    //排序
    private Integer sort;

    //状态 0:禁用 1:启用
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createUser;

    private Long updateUser;

}
