create table category
(
    id          bigint auto_increment comment '主键'
        primary key,
    name        varchar(32)   not null comment '分类名称',
    sort        int default 0 not null comment '顺序',
    status      int           null comment '分类状态 0:禁用，1:启用',
    create_time datetime      null comment '创建时间',
    update_time datetime      null comment '更新时间',
    create_user bigint        null comment '创建人',
    update_user bigint        null comment '修改人',
    constraint idx_category_name
        unique (name)
)
    comment '商品分类' collate = utf8mb3_bin;

create index idx_category_status_sort
    on category (status, sort);

create table employee
(
    id          bigint auto_increment comment '主键'
        primary key,
    name        varchar(32)                    not null comment '姓名',
    username    varchar(32)                    not null comment '用户名',
    password    varchar(64)                    not null comment '密码',
    phone       varchar(11)                    not null comment '手机号',
    sex         varchar(2)                     not null comment '性别',
    id_number   varchar(18)                    not null comment '身份证号',
    status      int         default 1          not null comment '状态 0:禁用，1:启用',
    create_time datetime                       null comment '创建时间',
    update_time datetime                       null comment '更新时间',
    create_user bigint                         null comment '创建人',
    update_user bigint                         null comment '修改人',
    role        varchar(20) default 'EMPLOYEE' not null comment 'ADMIN/EMPLOYEE',
    constraint idx_username
        unique (username)
)
    comment '员工信息' collate = utf8mb3_bin;

create index idx_employee_name
    on employee (name);

create table id_segment
(
    biz_tag     varchar(64)                        not null comment '业务标识'
        primary key,
    max_id      bigint                             not null comment '当前最大ID',
    step        int                                not null comment '号段步长',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    comment '号段表';

create table memo
(
    id             bigint auto_increment comment '主键ID'
        primary key,
    user_id        bigint                             not null comment '用户ID',
    title          varchar(100)                       null comment '标题（AI解析生成）',
    content        text                               not null comment '原始内容',
    parsed_content text                               null comment 'AI解析后的结构化内容（JSON格式）',
    priority       int      default 0                 null comment '优先级：0-普通，1-重要，2-紧急',
    status         int      default 0                 null comment '状态：0-待处理，1-处理中，2-已完成，3-已取消',
    due_date       datetime                           null comment '截止时间（AI解析提取）',
    remind_time    datetime                           null comment '提醒时间',
    is_reminded    int      default 0                 null comment '是否已提醒：0-否，1-是',
    tags           varchar(255)                       null comment '标签（逗号分隔）',
    create_time    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '备忘录/待办事项表';

create index idx_memo_due_date
    on memo (due_date);

create index idx_memo_remind_time
    on memo (remind_time, is_reminded);

create index idx_memo_status
    on memo (status);

create index idx_memo_user_id
    on memo (user_id);

create table order_detail
(
    id         bigint auto_increment comment '主键'
        primary key,
    name       varchar(32)    null comment '名字',
    image      varchar(255)   null comment '图片',
    order_id   bigint         not null comment '订单id',
    product_id bigint         null comment '商品id',
    spec_id    bigint         null comment '规格ID',
    spec_info  varchar(100)   null comment '规格信息（如：黑色128GB）',
    number     int default 1  not null comment '数量',
    amount     decimal(10, 2) not null comment '金额'
)
    comment '订单明细表' collate = utf8mb3_bin;

create index idx_order_detail_order
    on order_detail (order_id);

create table orders
(
    id               bigint auto_increment comment '主键'
        primary key,
    number           varchar(50)       null comment '订单号',
    status           int               null comment '订单状态：1待支付 2待处理 3处理中 4待自提 5已完成 6已取消',
    user_id          bigint            not null comment '下单用户',
    order_time       datetime          not null comment '下单时间',
    checkout_time    datetime          null comment '结账时间',
    pay_method       int     default 1 not null comment '支付方式 1微信,2支付宝',
    pay_status       tinyint default 0 not null comment '支付状态 0未支付 1已支付 2退款',
    amount           decimal(10, 2)    not null comment '实收金额',
    remark           varchar(100)      null comment '备注',
    user_name        varchar(32)       null comment '用户名称',
    cancel_reason    varchar(255)      null comment '订单取消原因',
    rejection_reason varchar(255)      null comment '订单拒绝原因',
    cancel_time      datetime          null comment '订单取消时间'
)
    comment '订单表' collate = utf8mb3_bin;

create index idx_orders_number
    on orders (number);

create index idx_orders_status_time
    on orders (status, order_time);

create index idx_orders_user_time
    on orders (user_id, order_time);

create table payment_callback_log
(
    id                bigint auto_increment
        primary key,
    out_trade_no      varchar(64)                        not null,
    transaction_id    varchar(64)                        not null,
    callback_type     varchar(32)                        not null,
    callback_status   varchar(16)                        not null,
    raw_callback_data text                               null,
    decrypted_data    text                               null,
    error_message     varchar(1024)                      null,
    handle_count      int      default 1                 null,
    callback_time     datetime                           not null,
    handle_time       datetime                           null,
    create_time       datetime default CURRENT_TIMESTAMP null,
    update_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_out_trade_no_transaction_id
        unique (out_trade_no, transaction_id)
);

create table phone_model
(
    id          bigint auto_increment comment '主键'
        primary key,
    brand       varchar(50)   not null comment '手机品牌（如Apple、Samsung、Xiaomi）',
    model_name  varchar(100)  not null comment '手机型号名称（如iPhone 15 Pro、Galaxy S24）',
    sort        int default 0 not null comment '排序',
    status      int default 1 null comment '状态 0:禁用 1:启用',
    create_time datetime      null comment '创建时间',
    update_time datetime      null comment '更新时间',
    create_user bigint        null comment '创建人',
    update_user bigint        null comment '修改人',
    constraint idx_phone_model_name
        unique (brand, model_name)
)
    comment '手机型号表' collate = utf8mb3_bin;

create index idx_phone_model_brand
    on phone_model (brand);

create index idx_phone_model_status_sort
    on phone_model (status, sort);

create table product
(
    id              bigint auto_increment comment '主键'
        primary key,
    name            varchar(100)   not null comment '商品名称',
    brand           varchar(50)    null comment '品牌',
    model           varchar(100)   null comment '型号',
    category_id     bigint         not null comment '菜品分类id',
    price           decimal(10, 2) not null comment '价格',
    stock           int default 0  null comment '库存',
    image           varchar(255)   null comment '图片',
    description     text           null comment '商品详情描述',
    specs           text           null comment '规格参数JSON',
    status          int default 1  null comment '0停售 1在售',
    create_time     datetime       null comment '创建时间',
    update_time     datetime       null comment '更新时间',
    create_user     bigint         null comment '创建人',
    update_user     bigint         null comment '修改人',
    alert_threshold int default 10 not null comment '低库存预警阈值',
    constraint idx_dish_name
        unique (name)
)
    comment '菜品' collate = utf8mb3_bin;

create index idx_product_category_status
    on product (category_id, status);

create index idx_product_status_update
    on product (status, update_time);

create table product_location
(
    id            bigint auto_increment comment '主键'
        primary key,
    product_id    bigint                             not null comment '商品ID',
    shelf_code    varchar(20)                        null comment '货架编号（如 A-01）',
    layer_num     int                                null comment '层数（从下往上）',
    position_code varchar(50)                        null comment '完整位置编码（如 A-01-3-05）',
    remark        varchar(200)                       null comment '备注说明',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_product_id
        unique (product_id)
)
    comment '商品位置表';

create index idx_position_code
    on product_location (position_code);

create index idx_shelf_code
    on product_location (shelf_code);

create table product_phone_model
(
    id             bigint auto_increment comment '主键'
        primary key,
    product_id     bigint not null comment '商品ID',
    phone_model_id bigint not null comment '手机型号ID',
    constraint uk_product_phone_model
        unique (product_id, phone_model_id)
)
    comment '商品与手机型号关联表' collate = utf8mb3_bin;

create index idx_phone_model_id
    on product_phone_model (phone_model_id);

create index idx_ppm_phone_model
    on product_phone_model (phone_model_id);

create index idx_ppm_product
    on product_phone_model (product_id);

create index idx_product_id
    on product_phone_model (product_id);

create table product_spec
(
    id         bigint auto_increment comment '主键'
        primary key,
    product_id bigint         not null comment '商品id',
    spec_name  varchar(32)    null comment '规格名称（如颜色、内存）',
    spec_value varchar(255)   null comment '规格值（如黑色、128GB）',
    spec_price decimal(10, 2) null comment '规格价格（空则用商品价格）',
    spec_stock int default 0  null comment '规格库存'
)
    comment '商品规格表' collate = utf8mb3_bin;

create index idx_product_spec_product
    on product_spec (product_id);

create table shopping_cart
(
    id          bigint auto_increment comment '主键'
        primary key,
    name        varchar(32)    null comment '商品名称',
    image       varchar(255)   null comment '图片',
    user_id     bigint         not null comment '主键',
    product_id  bigint         null comment '商品id',
    spec_info   varchar(100)   null comment '规格信息',
    number      int default 1  not null comment '数量',
    amount      decimal(10, 2) not null comment '金额',
    create_time datetime       null comment '创建时间'
)
    comment '购物车' collate = utf8mb3_bin;

create index idx_cart_user_product
    on shopping_cart (user_id, product_id);

create table stock_alert
(
    id              bigint auto_increment comment '主键'
        primary key,
    product_id      bigint            not null comment '商品ID',
    product_name    varchar(100)      not null comment '商品名称（快照）',
    current_stock   int               not null comment '触发时库存',
    alert_threshold int               not null comment '预警阈值',
    alert_status    tinyint default 0 not null comment '状态：0未处理 1已处理 2已忽略',
    alert_time      datetime          not null comment '预警时间',
    handle_time     datetime          null comment '处理时间',
    handle_user     bigint            null comment '处理人ID'
)
    comment '低库存预警表' collate = utf8mb3_bin;

create index idx_alert_product
    on stock_alert (product_id);

create index idx_alert_status
    on stock_alert (alert_status);

create table stock_check_plan
(
    id            bigint auto_increment comment '主键'
        primary key,
    plan_name     varchar(100)      not null comment '盘点计划名称',
    status        tinyint default 0 not null comment '状态：0待执行 1进行中 2已完成',
    create_user   bigint            not null comment '创建人',
    create_time   datetime          not null comment '创建时间',
    complete_time datetime          null comment '完成时间',
    remark        varchar(255)      null comment '备注'
)
    comment '盘点计划表' collate = utf8mb3_bin;

create table stock_check_record
(
    id            bigint auto_increment comment '主键'
        primary key,
    plan_id       bigint            not null comment '盘点计划ID',
    product_id    bigint            not null comment '商品ID',
    system_stock  int               not null comment '系统账面库存',
    actual_stock  int               not null comment '实盘库存',
    diff_quantity int               not null comment '差异数量（actual - system）',
    status        tinyint default 0 not null comment '状态：0待确认 1已调整 2已忽略',
    create_time   datetime          not null comment '盘点时间',
    update_time   datetime          null comment '调整时间'
)
    comment '盘点记录表' collate = utf8mb3_bin;

create index idx_check_record_plan
    on stock_check_record (plan_id);

create index idx_check_record_product
    on stock_check_record (product_id);

create table stock_log
(
    id              bigint auto_increment comment '主键'
        primary key,
    product_id      bigint       not null comment '商品ID',
    change_type     tinyint      not null comment '变动类型：1入库 2出库(下单) 3归还(取消) 4手动调整 5盘点',
    change_quantity int          not null comment '变动数量（正数增加，负数减少）',
    before_stock    int          not null comment '变动前库存',
    after_stock     int          not null comment '变动后库存',
    order_id        bigint       null comment '关联订单ID',
    remark          varchar(255) null comment '备注',
    operator_id     bigint       null comment '操作人ID（系统自动则为NULL）',
    create_time     datetime     not null comment '记录时间'
)
    comment '库存变动日志表' collate = utf8mb3_bin;

create index idx_stock_log_order
    on stock_log (order_id);

create index idx_stock_log_product
    on stock_log (product_id);

create index idx_stock_log_type_time
    on stock_log (change_type, create_time);

create table user
(
    id          bigint auto_increment comment '主键'
        primary key,
    openid      varchar(45)  null comment '微信用户唯一标识',
    name        varchar(32)  null comment '姓名',
    phone       varchar(11)  null comment '手机号',
    sex         varchar(2)   null comment '性别',
    id_number   varchar(18)  null comment '身份证号',
    avatar      varchar(500) null comment '头像',
    create_time datetime     null
)
    comment '用户信息' collate = utf8mb3_bin;

create index idx_user_openid
    on user (openid);

