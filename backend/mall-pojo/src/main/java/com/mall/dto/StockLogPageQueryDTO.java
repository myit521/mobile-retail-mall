package com.mall.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存日志分页查询
 */
@Data
public class StockLogPageQueryDTO implements Serializable {

    private int page;

    private int pageSize;

    //商品ID
    private Long productId;

    //变动类型
    private Integer changeType;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
