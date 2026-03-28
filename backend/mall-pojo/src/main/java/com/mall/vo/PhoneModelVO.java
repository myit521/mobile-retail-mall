package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneModelVO implements Serializable {

    private Long id;

    //手机品牌
    private String brand;

    //手机型号名称
    private String modelName;

    //排序
    private Integer sort;

    //状态 0:禁用 1:启用
    private Integer status;

    //更新时间
    private LocalDateTime updateTime;

}
