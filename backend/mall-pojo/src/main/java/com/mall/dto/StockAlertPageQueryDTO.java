package com.mall.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 预警记录分页查询
 */
@Data
public class StockAlertPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    //预警状态：0未处理 1已处理 2已忽略
    private Integer alertStatus;
}
