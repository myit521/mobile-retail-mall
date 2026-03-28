package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存变动日志展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long productId;

    //商品名称
    private String productName;

    //变动类型：1入库 2出库 3归还 4手动调整 5盘点
    private Integer changeType;

    //变动数量
    private Integer changeQuantity;

    //变动前库存
    private Integer beforeStock;

    //变动后库存
    private Integer afterStock;

    //关联订单ID
    private Long orderId;

    //备注
    private String remark;

    //操作人ID
    private Long operatorId;

    //操作人姓名
    private String operatorName;

    //记录时间
    private LocalDateTime createTime;
}
