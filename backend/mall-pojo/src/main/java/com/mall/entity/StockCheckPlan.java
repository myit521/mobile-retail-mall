package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 盘点计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCheckPlan implements Serializable {

    // 盘点状态常量
    public static final Integer STATUS_PENDING = 0;      // 待执行
    public static final Integer STATUS_IN_PROGRESS = 1;  // 进行中
    public static final Integer STATUS_COMPLETED = 2;    // 已完成

    private static final long serialVersionUID = 1L;

    private Long id;

    //盘点计划名称
    private String planName;

    //状态：0待执行 1进行中 2已完成
    private Integer status;

    //创建人
    private Long createUser;

    //创建时间
    private LocalDateTime createTime;

    //完成时间
    private LocalDateTime completeTime;

    //备注
    private String remark;
}
