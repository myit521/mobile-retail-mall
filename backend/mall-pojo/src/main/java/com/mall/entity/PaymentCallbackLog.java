package com.mall.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 支付回调日志表
 * 用于记录每次微信支付回调的原始数据和状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallbackLog {
    private Long id;
    private String outTradeNo;          // 商户订单号
    private String transactionId;       // 微信支付交易号
    private String callbackType;        // 回调类型：PAY_SUCCESS/REFUND_SUCCESS
    private String callbackStatus;      // 回调处理状态：SUCCESS/FAIL/PROCESSING
    private String rawCallbackData;     // 原始回调数据（加密前）
    private String decryptedData;       // 解密后的数据
    private String errorMessage;        // 错误信息
    private Integer handleCount;        // 处理次数（用于幂等）
    private LocalDateTime callbackTime; // 回调时间
    private LocalDateTime handleTime;   // 处理时间
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}
