-- 支付回调日志表
-- 用于记录微信支付回调的完整信息，支持幂等性和问题追溯
CREATE TABLE IF NOT EXISTS `payment_callback_log` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `out_trade_no` VARCHAR(64) NOT NULL COMMENT '商户订单号',
  `transaction_id` VARCHAR(64) NOT NULL COMMENT '微信支付交易号',
  `callback_type` VARCHAR(32) NOT NULL COMMENT '回调类型：PAY_SUCCESS/REFUND_SUCCESS',
  `callback_status` VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '处理状态：SUCCESS/FAIL/PROCESSING',
  `raw_callback_data` TEXT COMMENT '原始回调数据（加密前）',
  `decrypted_data` TEXT COMMENT '解密后的数据',
  `error_message` VARCHAR(1024) COMMENT '错误信息',
  `handle_count` INT(11) DEFAULT 1 COMMENT '处理次数',
  `callback_time` DATETIME NOT NULL COMMENT '回调时间',
  `handle_time` DATETIME COMMENT '处理时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_out_trade_no_transaction_id` (`out_trade_no`, `transaction_id`),
  KEY `idx_out_trade_no` (`out_trade_no`),
  KEY `idx_callback_time` (`callback_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志表';
