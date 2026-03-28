package com.mall.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CategoryPageQueryDTO implements Serializable {

    //页码
    private Integer page;

    //每页记录数
    private Integer pageSize;

    //分类名称
    private String name;

}
