package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 低库存预警展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAlertVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    //商品名称
    private String productName;

    //当前库存（最新）
    private Integer currentStock;

    //预警阈值
    private Integer alertThreshold;

    //预警状态：0未处理 1已处理 2已忽略
    private Integer alertStatus;

    //预警时间
    private LocalDateTime alertTime;

    //处理时间
    private LocalDateTime handleTime;

    //处理人姓名
    private String handleUserName;
}
