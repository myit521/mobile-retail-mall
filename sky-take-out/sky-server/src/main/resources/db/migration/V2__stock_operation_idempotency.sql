create table stock_operation
(
    id          bigint auto_increment primary key,
    order_id    bigint                              not null comment '关联订单ID',
    action_key  varchar(64)                         not null comment '库存操作业务键',
    product_id  bigint                              null comment '商品ID；整单操作不适用',
    quantity    int                                 null comment '整单实际变动总数量',
    outcome     varchar(32)                         not null comment '处理结果',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_stock_operation_order_action unique (order_id, action_key)
)
    comment '库存操作幂等记录表' collate = utf8mb3_bin;
