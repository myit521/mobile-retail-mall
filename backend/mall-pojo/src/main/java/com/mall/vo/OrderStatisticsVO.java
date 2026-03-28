package com.mall.vo;

import lombok.Data;
import java.io.Serializable;

@Data
public class OrderStatisticsVO implements Serializable {
    //待处理数量
    // Mall mode: pending process count
    private Integer toBeConfirmed;

    //处理中数量
    // Mall mode: processing count
    private Integer confirmed;

    //待自提数量
    // Mall mode: ready for pickup count
    private Integer deliveryInProgress;
}
