# API 接口文档

> 基础URL: `http://localhost:8080`
> 文档版本: 1.2
> Knife4j在线文档: `http://localhost:8080/doc.html`
> 最后更新: 2026-03-04

---

## 通用说明

### 认证方式

管理端和用户端使用不同的 JWT Token：

| 端 | Header名称 | 获取方式 |
|----|-----------|---------|
| Admin | `token` | 员工登录接口返回 |
| User | `authentication` | 用户登录接口返回 |

> 除登录接口外，所有接口均需在请求头中携带对应 Token。

### 通用响应格式

```json
{
  "code": 1,
  "msg": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 1=成功，0=失败 |
| msg | String | 提示信息 |
| data | Object | 响应数据（可选） |

### 分页响应格式

当 `data` 为分页结果时：

```json
{
  "code": 1,
  "data": {
    "total": 100,
    "records": [...]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| total | long | 总记录数 |
| records | List | 当前页数据集合 |

---

# 一、Admin 管理端接口

## 1. 员工管理

### 1.1 员工登录

- **POST** `/admin/employee/login`

> **安全说明：** 同一账号连续登录失败 **5次** 后将被锁定 **15分钟**，锁定期间返回错误提示及剩余分钟数。

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 员工ID |
| userName | String | 用户名 |
| name | String | 姓名 |
| token | String | JWT令牌 |

**请求示例:**

```json
POST /admin/employee/login
{
  "username": "admin",
  "password": "123456"
}
```

---

### 1.2 员工退出

- **POST** `/admin/employee/logout`

**请求参数:** 无

**响应:** `Result<String>`

---

### 1.3 新增员工

- **POST** `/admin/employee`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 否 | 员工ID（新增时不传） |
| username | String | 是 | 用户名 |
| name | String | 是 | 姓名 |
| phone | String | 否 | 手机号 |
| sex | String | 否 | 性别 |
| idNumber | String | 否 | 身份证号 |

**请求示例:**

```json
POST /admin/employee
{
  "username": "zhangsan",
  "name": "张三",
  "phone": "13800138000",
  "sex": "1",
  "idNumber": "110101199001011234"
}
```

---

### 1.4 员工分页查询

- **GET** `/admin/employee/page`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 是 | 页码 |
| pageSize | int | 是 | 每页条数 |
| name | String | 否 | 员工姓名（模糊查询） |

**响应 data:** PageResult，records 为 Employee 列表

**请求示例:**

```
GET /admin/employee/page?page=1&pageSize=10&name=张
```

---

### 1.5 启用/禁用员工账号

- **POST** `/admin/employee/status/{status}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| status | Integer | 0=禁用 1=启用 |

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 员工ID |

**请求示例:**

```
POST /admin/employee/status/0?id=10
```

---

### 1.6 根据ID查询员工

- **GET** `/admin/employee/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 员工ID |

**响应 data:** Employee 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 员工ID |
| username | String | 用户名 |
| name | String | 姓名 |
| phone | String | 手机号 |
| sex | String | 性别 |
| idNumber | String | 身份证号 |
| status | Integer | 状态 0=禁用 1=启用 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

---

### 1.7 编辑员工信息

- **PUT** `/admin/employee`

**请求体 (JSON):** 同 1.3 新增员工（必须包含 id）

**请求示例:**

```json
PUT /admin/employee
{
  "id": 10,
  "username": "zhangsan",
  "name": "张三",
  "phone": "13900139000"
}
```

---

### 1.8 修改密码

- **POST** `/admin/employee/editPassword`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| empId | Long | 是 | 员工ID |
| oldPassword | String | 是 | 旧密码 |
| newPassword | String | 是 | 新密码 |

**请求示例:**

```json
POST /admin/employee/editPassword
{
  "empId": 1,
  "oldPassword": "123456",
  "newPassword": "654321"
}
```

---

## 2. 分类管理

### 2.1 新增分类

- **POST** `/admin/category`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 否 | 分类ID |
| name | String | 是 | 分类名称 |
| sort | Integer | 是 | 排序 |

**请求示例:**

```json
POST /admin/category
{
  "name": "手机壳",
  "sort": 1
}
```

---

### 2.2 分类分页查询

- **GET** `/admin/category/page`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 是 | 页码 |
| pageSize | int | 是 | 每页条数 |
| name | String | 否 | 分类名称 |

**响应 data:** PageResult，records 为 Category 列表

---

### 2.3 删除分类

- **DELETE** `/admin/category`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 分类ID |

**请求示例:**

```
DELETE /admin/category?id=5
```

---

### 2.4 修改分类

- **PUT** `/admin/category`

**请求体 (JSON):** 同 2.1（必须包含 id）

---

### 2.5 启用/禁用分类

- **POST** `/admin/category/status/{status}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| status | Integer | 0=禁用 1=启用 |

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 分类ID |

---

### 2.6 根据类型查询分类列表

- **GET** `/admin/category/list`

**请求参数:** 无

**响应 data:** `List<Category>`

---

## 3. 商品管理

### 3.1 新增商品

- **POST** `/admin/product`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 商品名称 |
| brand | String | 否 | 品牌 |
| model | String | 否 | 型号 |
| categoryId | Long | 是 | 分类ID |
| price | BigDecimal | 是 | 价格 |
| stock | Integer | 是 | 库存 |
| image | String | 否 | 图片URL |
| description | String | 否 | 描述 |
| specs | String | 否 | 规格参数JSON |
| status | Integer | 否 | 0=停售 1=起售 |
| specList | List | 否 | 规格列表（见下方） |
| phoneModelIds | List\<Long\> | 否 | 适配手机型号ID列表 |

**specList 子对象:**

| 字段 | 类型 | 说明 |
|------|------|------|
| specName | String | 规格名称（如颜色、内存） |
| specValue | String | 规格值（如黑色、128GB） |
| specPrice | BigDecimal | 规格价格（空则用商品价格） |
| specStock | Integer | 规格库存 |

**请求示例:**

```json
POST /admin/product
{
  "name": "iPhone 15 Pro 手机壳",
  "brand": "品胜",
  "categoryId": 1,
  "price": 39.90,
  "stock": 100,
  "image": "https://xxx.com/image.jpg",
  "description": "防摔透明手机壳",
  "status": 1,
  "specList": [
    { "specName": "颜色", "specValue": "透明", "specPrice": 39.90, "specStock": 50 },
    { "specName": "颜色", "specValue": "黑色", "specPrice": 42.90, "specStock": 50 }
  ],
  "phoneModelIds": [1, 2, 3]
}
```

---

### 3.2 商品分页查询

- **GET** `/admin/product/page`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 是 | 页码 |
| pageSize | int | 是 | 每页条数 |
| name | String | 否 | 商品名称（模糊） |
| categoryId | Integer | 否 | 分类ID |
| status | Integer | 否 | 0=停售 1=起售 |
| phoneModelId | Long | 否 | 手机型号ID（按型号筛选） |

**响应 data:** PageResult，records 为 ProductVO 列表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 商品ID |
| name | String | 商品名称 |
| brand | String | 品牌 |
| model | String | 型号 |
| categoryId | Long | 分类ID |
| price | BigDecimal | 价格 |
| stock | Integer | 库存 |
| image | String | 图片 |
| status | Integer | 状态 |
| updateTime | LocalDateTime | 更新时间 |
| categoryName | String | 分类名称 |

---

### 3.3 商品批量删除

- **DELETE** `/admin/product`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| ids | List\<Long\> | 是 | 商品ID列表，逗号分隔 |

**请求示例:**

```
DELETE /admin/product?ids=1,2,3
```

---

### 3.4 根据ID查询商品

- **GET** `/admin/product/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 商品ID |

**响应 data:** ProductVO（含 specList 和 phoneModelList）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 商品ID |
| name | String | 名称 |
| brand | String | 品牌 |
| model | String | 型号 |
| categoryId | Long | 分类ID |
| price | BigDecimal | 价格 |
| stock | Integer | 库存 |
| image | String | 图片 |
| description | String | 描述 |
| specs | String | 规格参数JSON |
| status | Integer | 状态 |
| categoryName | String | 分类名称 |
| specList | List | 规格列表 |
| phoneModelList | List | 适配手机型号列表 |

---

### 3.5 根据分类ID查询商品列表

- **GET** `/admin/product/list`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| categoryId | Long | 是 | 分类ID |

**响应 data:** `List<Product>`

---

### 3.6 修改商品状态（起售/停售）

- **POST** `/admin/product/status/{status}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| status | Integer | 0=停售 1=起售 |

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 商品ID |

---

### 3.7 修改商品

- **PUT** `/admin/product`

**请求体 (JSON):** 同 3.1 新增商品（必须包含 id）

---

## 4. 手机型号管理

### 4.1 新增手机型号

- **POST** `/admin/phoneModel`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| brand | String | 是 | 手机品牌（如 Apple） |
| modelName | String | 是 | 型号名称（如 iPhone 15 Pro） |
| sort | Integer | 否 | 排序 |
| status | Integer | 否 | 0=禁用 1=启用 |

**请求示例:**

```json
POST /admin/phoneModel
{
  "brand": "Apple",
  "modelName": "iPhone 15 Pro",
  "sort": 1,
  "status": 1
}
```

---

### 4.2 手机型号分页查询

- **GET** `/admin/phoneModel/page`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 是 | 页码 |
| pageSize | int | 是 | 每页条数 |
| brand | String | 否 | 品牌（模糊） |
| modelName | String | 否 | 型号名称（模糊） |
| status | Integer | 否 | 状态 |

**响应 data:** PageResult，records 为 PhoneModel 列表

---

### 4.3 根据ID查询手机型号

- **GET** `/admin/phoneModel/{id}`

**响应 data:** PhoneModel 对象

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | ID |
| brand | String | 品牌 |
| modelName | String | 型号名称 |
| sort | Integer | 排序 |
| status | Integer | 状态 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

---

### 4.4 修改手机型号

- **PUT** `/admin/phoneModel`

**请求体 (JSON):** 同 4.1（必须包含 id）

---

### 4.5 批量删除手机型号

- **DELETE** `/admin/phoneModel`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| ids | List\<Long\> | 是 | 型号ID列表，逗号分隔 |

---

### 4.6 启用/禁用手机型号

- **POST** `/admin/phoneModel/status/{status}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| status | Integer | 0=禁用 1=启用 |

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 型号ID |

---

### 4.7 查询所有启用的手机型号

- **GET** `/admin/phoneModel/list`

**请求参数:** 无

**响应 data:** `List<PhoneModel>`

---

## 5. 订单管理

### 5.1 订单搜索

- **GET** `/admin/order/conditionSearch`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 是 | 页码 |
| pageSize | int | 是 | 每页条数 |
| number | String | 否 | 订单号 |
| phone | String | 否 | 手机号 |
| status | Integer | 否 | 订单状态 |
| beginTime | LocalDateTime | 否 | 开始时间（yyyy-MM-dd HH:mm:ss） |
| endTime | LocalDateTime | 否 | 结束时间（yyyy-MM-dd HH:mm:ss） |

**订单状态枚举:**

| 值 | 说明 |
|----|------|
| 1 | 待支付 |
| 2 | 待处理 |
| 3 | 处理中 |
| 4 | 待自提 |
| 5 | 已完成 |
| 6 | 已取消 |

**响应 data:** PageResult，records 为 OrderVO 列表

---

### 5.2 各状态订单数量统计

- **GET** `/admin/order/statistics`

**请求参数:** 无

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| toBeConfirmed | Integer | 待处理数量 |
| confirmed | Integer | 处理中数量 |
| deliveryInProgress | Integer | 待自提数量 |

---

### 5.3 查询订单详情

- **GET** `/admin/order/details/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单ID |

**响应 data:** OrderVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单ID |
| number | String | 订单号 |
| status | Integer | 订单状态 |
| userId | Long | 用户ID |
| orderTime | LocalDateTime | 下单时间 |
| checkoutTime | LocalDateTime | 结账时间 |
| payMethod | Integer | 支付方式 1=微信 2=支付宝 |
| payStatus | Integer | 支付状态 0=未支付 1=已支付 2=退款 |
| amount | BigDecimal | 实收金额 |
| remark | String | 备注 |
| userName | String | 用户名 |
| cancelReason | String | 取消原因 |
| rejectionReason | String | 拒绝原因 |
| cancelTime | LocalDateTime | 取消时间 |
| orderDetailList | List | 订单明细列表 |
| orderDishes | String | 订单商品信息字符串 |

**orderDetailList 子对象:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 明细ID |
| name | String | 商品名称 |
| orderId | Long | 订单ID |
| productId | Long | 商品ID |
| specId | Long | 规格ID |
| specInfo | String | 规格信息 |
| number | Integer | 数量 |
| amount | BigDecimal | 金额 |
| image | String | 图片 |

---

### 5.4 确认订单

- **PUT** `/admin/order/confirm`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 订单ID |
| status | Integer | 否 | 订单状态 |

**请求示例:**

```json
PUT /admin/order/confirm
{ "id": 10 }
```

---

### 5.5 驳回订单

- **PUT** `/admin/order/rejection`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 订单ID |
| rejectionReason | String | 是 | 驳回原因 |

**请求示例:**

```json
PUT /admin/order/rejection
{
  "id": 10,
  "rejectionReason": "库存不足"
}
```

---

### 5.6 取消订单

- **PUT** `/admin/order/cancel`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 订单ID |
| cancelReason | String | 是 | 取消原因 |

**请求示例:**

```json
PUT /admin/order/cancel
{
  "id": 10,
  "cancelReason": "用户要求取消"
}
```

---

## 6. 店铺管理

### 6.1 设置营业状态

- **PUT** `/admin/shop/{status}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| status | Integer | 0=打烊 1=营业中 |

---

### 6.2 获取营业状态

- **GET** `/admin/shop/status`

**请求参数:** 无

**响应 data:** Integer（0=打烊 1=营业中）

---

## 7. 库存管理

### 7.1 手动调整库存

- **PUT** `/admin/stock/adjust`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| productId | Long | 是 | 商品ID |
| quantity | Integer | 是 | 调整数量（正数增加，负数减少） |
| remark | String | 否 | 备注 |

**请求示例:**

```json
PUT /admin/stock/adjust
{
  "productId": 1,
  "quantity": 50,
  "remark": "到货补充库存"
}
```

---

### 7.2 分页查询库存日志

- **GET** `/admin/stock/logs`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 是 | 页码 |
| pageSize | int | 是 | 每页条数 |
| productId | Long | 否 | 商品ID |
| changeType | Integer | 否 | 变动类型 |
| beginTime | LocalDateTime | 否 | 开始时间 |
| endTime | LocalDateTime | 否 | 结束时间 |

**变动类型枚举:**

| 值 | 说明 |
|----|------|
| 1 | 入库 |
| 2 | 出库（下单） |
| 3 | 归还（取消） |
| 4 | 手动调整 |
| 5 | 盘点 |

**响应 data:** PageResult，records 为 StockLogVO 列表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 日志ID |
| productId | Long | 商品ID |
| productName | String | 商品名称 |
| changeType | Integer | 变动类型 |
| changeQuantity | Integer | 变动数量 |
| beforeStock | Integer | 变动前库存 |
| afterStock | Integer | 变动后库存 |
| orderId | Long | 关联订单ID |
| remark | String | 备注 |
| operatorId | Long | 操作人ID |
| operatorName | String | 操作人姓名 |
| createTime | LocalDateTime | 记录时间 |

---

### 7.3 分页查询预警记录

- **GET** `/admin/stock/alerts`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 是 | 页码 |
| pageSize | int | 是 | 每页条数 |
| alertStatus | Integer | 否 | 状态：0=未处理 1=已处理 2=已忽略 |

**响应 data:** PageResult，records 为 StockAlertVO 列表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 预警ID |
| productId | Long | 商品ID |
| productName | String | 商品名称 |
| currentStock | Integer | 触发时库存 |
| alertThreshold | Integer | 预警阈值 |
| alertStatus | Integer | 状态 |
| alertTime | LocalDateTime | 预警时间 |
| handleTime | LocalDateTime | 处理时间 |
| handleUserName | String | 处理人姓名 |

---

### 7.4 处理预警

- **PUT** `/admin/stock/alerts/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 预警ID |

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 是 | 1=已处理 2=已忽略 |

**请求示例:**

```
PUT /admin/stock/alerts/5?status=1
```

---

### 7.5 创建盘点计划

- **POST** `/admin/stock/check/plan`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| planName | String | 是 | 盘点计划名称 |
| remark | String | 否 | 备注 |

**请求示例:**

```json
POST /admin/stock/check/plan
{
  "planName": "2026年3月月度盘点",
  "remark": "全量盘点"
}
```

---

### 7.6 查询盘点计划列表

- **GET** `/admin/stock/check/plans`

**请求参数:** 无

**响应 data:** `List<StockCheckPlan>`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 计划ID |
| planName | String | 计划名称 |
| status | Integer | 0=待执行 1=进行中 2=已完成 |
| createUser | Long | 创建人ID |
| createTime | LocalDateTime | 创建时间 |
| completeTime | LocalDateTime | 完成时间 |
| remark | String | 备注 |

---

### 7.7 完成盘点计划

- **PUT** `/admin/stock/check/plan/{id}/complete`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 计划ID |

---

### 7.8 提交盘点记录

- **POST** `/admin/stock/check/record`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| planId | Long | 是 | 盘点计划ID |
| productId | Long | 是 | 商品ID |
| actualStock | Integer | 是 | 实盘库存 |

**请求示例:**

```json
POST /admin/stock/check/record
{
  "planId": 1,
  "productId": 10,
  "actualStock": 95
}
```

---

### 7.9 查询盘点记录

- **GET** `/admin/stock/check/records/{planId}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| planId | Long | 盘点计划ID |

**响应 data:** `List<StockCheckRecord>`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录ID |
| planId | Long | 计划ID |
| productId | Long | 商品ID |
| systemStock | Integer | 系统账面库存 |
| actualStock | Integer | 实盘库存 |
| diffQuantity | Integer | 差异数量 |
| status | Integer | 0=待确认 1=已调整 2=已忽略 |
| createTime | LocalDateTime | 盘点时间 |
| updateTime | LocalDateTime | 调整时间 |

---

### 7.10 确认盘点记录（调整库存）

- **PUT** `/admin/stock/check/record/{id}/confirm`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 盘点记录ID |

> 确认后系统将自动将该商品库存调整为实盘库存，并记录库存变动日志。

---

## 8. 商品位置管理

### 8.1 设置商品位置

- **POST** `/admin/productLocation`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| productId | Long | 是 | 商品ID |
| shelfCode | String | 否 | 货架编号（如 A-01） |
| layerNum | Integer | 否 | 层数（从下往上） |
| positionCode | String | 否 | 完整位置编码（如 A-01-3-05） |
| remark | String | 否 | 备注说明 |

> 如果该商品已有位置记录则更新，否则新增。

**请求示例:**

```json
POST /admin/productLocation
{
  "productId": 1,
  "shelfCode": "A-01",
  "layerNum": 3,
  "positionCode": "A-01-3-05",
  "remark": "靠近入口"
}
```

---

### 8.2 根据商品ID查询位置

- **GET** `/admin/productLocation/{productId}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| productId | Long | 商品ID |

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 位置记录ID |
| productId | Long | 商品ID |
| shelfCode | String | 货架编号 |
| layerNum | Integer | 层数 |
| positionCode | String | 完整位置编码 |
| remark | String | 备注 |

---

### 8.3 查询商品位置列表

- **GET** `/admin/productLocation/list`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| shelfCode | String | 否 | 货架编号（模糊查询） |

**响应 data:** `List<ProductLocationVO>`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 位置记录ID |
| productId | Long | 商品ID |
| productName | String | 商品名称 |
| productImage | String | 商品图片 |
| shelfCode | String | 货架编号 |
| layerNum | Integer | 层数 |
| positionCode | String | 完整位置编码 |
| remark | String | 备注 |

**请求示例:**

```
GET /admin/productLocation/list?shelfCode=A
```

---

### 8.4 删除商品位置

- **DELETE** `/admin/productLocation/{productId}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| productId | Long | 商品ID |

---

## 9. 通用接口

### 9.1 文件上传

- **POST** `/admin/common/upload`

**请求参数:** `multipart/form-data`

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 上传文件 |

**响应 data:** String（文件访问URL）

---

# 二、User 用户端接口

## 1. 用户登录

### 1.1 微信登录

- **POST** `/user/user/login`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | String | 是 | 微信授权码 |

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户ID |
| openid | String | 微信openid |
| token | String | JWT令牌 |

---

## 2. 分类查询

### 2.1 查询分类列表

- **GET** `/user/category/list`

**请求参数:** 无

**响应 data:** `List<Category>`

---

## 3. 商品浏览

### 3.1 根据分类ID查询商品

- **GET** `/user/product/list`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| categoryId | Long | 是 | 分类ID |

**响应 data:** `List<ProductVO>`（含 specList 和 phoneModelList）

---

### 3.2 根据手机型号ID查询商品

- **GET** `/user/product/listByPhoneModel`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| phoneModelId | Long | 是 | 手机型号ID |

**响应 data:** `List<ProductVO>`（含 specList 和 phoneModelList）

**请求示例:**

```
GET /user/product/listByPhoneModel?phoneModelId=1
```

---

### 3.3 查询商品摆放位置

- **GET** `/user/product/location/{productId}`

> 用户选定商品后，点击"查看位置"时调用此接口获取商品在仓库/货架中的摆放位置。

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| productId | Long | 商品ID |

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 位置记录ID |
| productId | Long | 商品ID |
| shelfCode | String | 货架编号（如 A-01） |
| layerNum | Integer | 层数（从下往上） |
| positionCode | String | 完整位置编码（如 A-01-3-05） |
| remark | String | 备注说明 |

**请求示例:**

```
GET /user/product/location/1
```

**响应示例:**

```json
{
  "code": 1,
  "data": {
    "id": 1,
    "productId": 1,
    "shelfCode": "A-01",
    "layerNum": 3,
    "positionCode": "A-01-3-05",
    "remark": "靠近入口"
  }
}
```

---

## 4. 手机型号查询

### 4.1 查询所有启用的手机型号

- **GET** `/user/phoneModel/list`

**请求参数:** 无

**响应 data:** `List<PhoneModel>`

---

### 4.2 根据品牌查询手机型号

- **GET** `/user/phoneModel/listByBrand`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| brand | String | 是 | 品牌名称 |

**响应 data:** `List<PhoneModel>`

**请求示例:**

```
GET /user/phoneModel/listByBrand?brand=Apple
```

---

## 5. 购物车

### 5.1 添加购物车

- **POST** `/user/shoppingCart/add`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| productId | Long | 是 | 商品ID |
| specInfo | String | 否 | 规格信息 |

> 添加前会校验商品库存是否充足，库存不足将返回错误。

**请求示例:**

```json
POST /user/shoppingCart/add
{
  "productId": 1,
  "specInfo": "黑色 128GB"
}
```

---

### 5.2 查看购物车

- **GET** `/user/shoppingCart/list`

**请求参数:** 无

**响应 data:** `List<ShoppingCart>`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 购物车ID |
| name | String | 商品名称 |
| userId | Long | 用户ID |
| productId | Long | 商品ID |
| specInfo | String | 规格信息 |
| number | Integer | 数量 |
| amount | BigDecimal | 单价 |
| image | String | 图片 |
| createTime | LocalDateTime | 添加时间 |

---

### 5.3 清空购物车

- **DELETE** `/user/shoppingCart/clean`

**请求参数:** 无

---

### 5.4 删除购物车中一个商品（数量-1）

- **POST** `/user/shoppingCart/sub`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| productId | Long | 是 | 商品ID |
| specInfo | String | 否 | 规格信息 |

> 当商品数量为1时，执行删除操作。

---

## 6. 订单管理

### 6.1 用户下单

- **POST** `/user/order/submit`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| addressBookId | Long | 否 | 历史遗留字段，商城模式不使用 |
| payMethod | int | 是 | 支付方式 1=微信 2=支付宝 |
| remark | String | 否 | 备注 |
| amount | BigDecimal | 是 | 总金额 |
| tablewareNumber | Integer | 否 | 餐具数量 |
| tablewareStatus | Integer | 否 | 餐具数量状态 |

> 下单时系统会自动扣减库存（乐观锁防超卖），库存不足将返回错误。

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单ID |
| orderNumber | String | 订单号 |
| orderAmount | BigDecimal | 订单金额 |
| orderTime | LocalDateTime | 下单时间 |

---

### 6.2 订单支付

- **PUT** `/user/order/payment`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderNumber | String | 是 | 订单号 |
| payMethod | Integer | 是 | 支付方式 |

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| nonceStr | String | 随机字符串 |
| paySign | String | 签名 |
| timeStamp | String | 时间戳 |
| signType | String | 签名算法 |
| packageStr | String | prepay_id 参数值 |

---

### 6.3 历史订单列表

- **GET** `/user/order/historyOrders`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 是 | 页码 |
| pageSize | int | 是 | 每页条数 |
| status | Integer | 否 | 订单状态筛选 |

**响应 data:** PageResult，records 为 OrderVO 列表（含 orderDetailList）

---

### 6.4 查询订单详情

- **GET** `/user/order/orderDetail/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单ID |

**响应 data:** OrderVO（含 orderDetailList 和 orderDishes）

---

### 6.5 取消订单

- **PUT** `/user/order/cancel/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 订单ID |

> 取消订单后系统将自动归还已扣减的库存。已支付订单将自动发起退款。

---

### 6.6 再来一单

- **POST** `/user/order/repetition/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 原订单ID |

> 根据原订单信息重新创建一个待支付订单。

---

## 7. 店铺状态

### 7.1 获取营业状态

- **GET** `/user/shop/status`

**请求参数:** 无

**响应 data:** Integer（0=打烊 1=营业中）

---

## 8. 备忘录

### 8.1 创建备忘录

- **POST** `/user/memo`

> 支持自然语言输入，系统将自动调用 AI（SiliconFlow）解析出标题、截止时间、优先级、标签等信息。可通过 `enableAiParse: false` 关闭解析。

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | 是 | 备忘内容（支持自然语言，如"明天下午3点前整理库存"） |
| priority | Integer | 否 | 优先级 0=普通 1=重要 2=紧急（不填由AI判断） |
| dueDate | LocalDateTime | 否 | 截止时间（手动指定，会覆盖AI解析结果） |
| remindTime | LocalDateTime | 否 | 提醒时间 |
| tags | String | 否 | 标签，多个用逗号分隔 |
| enableAiParse | Boolean | 否 | 是否启用AI解析，默认 true |

**响应 data:** MemoVO（见 8.5）

**请求示例:**

```json
POST /user/memo
{
  "content": "明天下午3点前整理A区货架库存，重要！",
  "enableAiParse": true
}
```

---

### 8.2 更新备忘录

- **PUT** `/user/memo`

**请求体 (JSON):**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | Long | 是 | 备忘录ID |
| content | String | 否 | 内容 |
| priority | Integer | 否 | 优先级 |
| dueDate | LocalDateTime | 否 | 截止时间 |
| remindTime | LocalDateTime | 否 | 提醒时间 |
| tags | String | 否 | 标签 |
| enableAiParse | Boolean | 否 | 内容变更时是否重新AI解析，默认 true |

---

### 8.3 更新备忘录状态

- **PUT** `/user/memo/{id}/status`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 备忘录ID |

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | 是 | 0=待处理 1=处理中 2=已完成 3=已取消 |

---

### 8.4 完成备忘录（快捷）

- **PUT** `/user/memo/{id}/complete`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 备忘录ID |

---

### 8.5 取消备忘录（快捷）

- **PUT** `/user/memo/{id}/cancel`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 备忘录ID |

---

### 8.6 删除备忘录

- **DELETE** `/user/memo/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 备忘录ID |

---

### 8.7 查询备忘录详情

- **GET** `/user/memo/{id}`

**Path参数:**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 备忘录ID |

**响应 data: MemoVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 备忘录ID |
| title | String | 标题（AI生成） |
| content | String | 原始内容 |
| parsedContent | String | AI解析的结构化JSON |
| priority | Integer | 优先级 |
| priorityDesc | String | 优先级描述（普通/重要/紧急） |
| status | Integer | 状态 |
| statusDesc | String | 状态描述（待处理/处理中/已完成/已取消） |
| dueDate | LocalDateTime | 截止时间 |
| remindTime | LocalDateTime | 提醒时间 |
| isReminded | Integer | 是否已提醒 |
| tags | String | 标签 |
| isOverdue | Boolean | 是否已过期 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

---

### 8.8 分页查询备忘录

- **GET** `/user/memo/page`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| pageSize | int | 否 | 每页条数，默认 10 |
| status | Integer | 否 | 状态筛选 |
| priority | Integer | 否 | 优先级筛选 |
| keyword | String | 否 | 关键词（搜索标题/内容） |
| tag | String | 否 | 标签筛选 |

> 返回结果默认排序：待处理优先，同状态按优先级降序，再按截止时间升序。

**响应 data:** PageResult，records 为 MemoVO 列表

---

### 8.9 AI解析预览

- **POST** `/user/memo/parse`

> 仅解析文本，不保存。可用于前端展示AI解析结果供用户确认。

**请求体:** 纯文本字符串（`Content-Type: application/json`）

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| title | String | 解析出的标题 |
| dueDate | LocalDateTime | 解析出的截止时间 |
| priority | Integer | 解析出的优先级 |
| tags | List\<String\> | 解析出的标签列表 |
| keyPoints | List\<String\> | 解析出的关键任务点 |
| success | Boolean | 是否解析成功 |
| errorMessage | String | 失败原因（success=false时有值） |

**请求示例:**

```
POST /user/memo/parse
"明天下午3点前整理A区货架库存，比较重要"
```

---

### 8.10 获取备忘录统计

- **GET** `/user/memo/stats`

**请求参数:** 无

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| pendingCount | Integer | 待处理数量 |
| processingCount | Integer | 处理中数量 |
| completedCount | Integer | 已完成数量 |
| cancelledCount | Integer | 已取消数量 |
| totalCount | Integer | 总数量 |

---

# 三、Admin 数据报表接口

## 1. 数据概览

### 1.1 数据概览

- **GET** `/admin/report/overview`

**请求参数:** 无

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| todayTurnover | BigDecimal | 今日营业额（已完成订单） |
| todayOrderCount | Integer | 今日订单数 |
| todayValidOrderCount | Integer | 今日有效订单数（已完成） |
| waitingOrderCount | Integer | 待处理订单数 |
| processingOrderCount | Integer | 处理中订单数 |
| productCount | Integer | 商品总数 |
| onSaleProductCount | Integer | 在售商品数 |
| lowStockProductCount | Integer | 库存预警商品数 |

---

## 2. 营业额统计

### 2.1 营业额趋势

- **GET** `/admin/report/turnover`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| begin | LocalDate | 否 | 开始日期（yyyy-MM-dd），默认近7天 |
| end | LocalDate | 否 | 结束日期（yyyy-MM-dd），默认今天 |

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| dateList | List\<String\> | 日期列表（ECharts X轴） |
| turnoverList | List\<BigDecimal\> | 每日营业额（ECharts 数据） |
| totalTurnover | BigDecimal | 区间总营业额 |

**请求示例:**

```
GET /admin/report/turnover?begin=2026-03-01&end=2026-03-04
```

---

## 3. 订单统计

### 3.1 订单趋势统计

- **GET** `/admin/report/orderStatistics`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| begin | LocalDate | 否 | 开始日期，默认近7天 |
| end | LocalDate | 否 | 结束日期，默认今天 |

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| dateList | List\<String\> | 日期列表 |
| orderCountList | List\<Integer\> | 每日订单数 |
| validOrderCountList | List\<Integer\> | 每日有效订单数（已完成） |
| totalOrderCount | Integer | 区间订单总数 |
| validOrderCount | Integer | 区间有效订单总数 |
| completionRate | Double | 订单完成率（%） |

---

## 4. 商品销量排行

### 4.1 销量排行榜

- **GET** `/admin/report/salesRanking`

**Query参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| begin | LocalDate | 否 | 开始日期 |
| end | LocalDate | 否 | 结束日期 |
| limit | Integer | 否 | 排行数量，默认 10 |

**响应 data:**

| 字段 | 类型 | 说明 |
|------|------|------|
| nameList | List\<String\> | 商品名称列表（ECharts图例） |
| salesList | List\<Integer\> | 销量列表 |
| amountList | List\<BigDecimal\> | 销售额列表 |
| details | List | 详细数据（见下方） |

**details 子对象:**

| 字段 | 类型 | 说明 |
|------|------|------|
| productId | Long | 商品ID |
| productName | String | 商品名称 |
| image | String | 商品图片 |
| salesCount | Integer | 销量 |
| salesAmount | BigDecimal | 销售额 |

**请求示例:**

```
GET /admin/report/salesRanking?begin=2026-03-01&end=2026-03-04&limit=5
```
