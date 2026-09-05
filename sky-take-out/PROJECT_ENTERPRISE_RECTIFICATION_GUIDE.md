# 商业项目级整改蓝图

> 这不是“如何让 demo 看起来更完整”的文档。
>
> 这是一份按商业项目标准审视当前代码库后给出的整改方案。
> 目标不是继续堆功能，而是把项目从“能跑”拉到“能维护、能扩展、敢上线”。
>
> 建议和下面两份文档一起使用：
> - [ONLINE_READINESS_GUIDE.md](./ONLINE_READINESS_GUIDE.md)
> - [ONLINE_ACTION_GUIDE.md](./ONLINE_ACTION_GUIDE.md)

---

## 1. 先给结论

如果按商业项目标准打分，这个项目现在还不能算“正式项目”，更接近：

- 一个已经完成了部分业务改造的课程项目
- 一个具备了一些真实业务雏形，但底层治理明显不足的单体系统

我会很直接地说：

- 它不是纯 demo 了
- 但它也绝对还没达到“可以放心持续迭代并上线放量”的标准

当前最大的风险，不是页面还残留了多少外卖词，而是：

1. 核心业务安全边界不完整
2. 登录和鉴权链路不统一
3. 支付回调和订单状态流转不够可靠
4. 同步耦合过重，没有事件化能力
5. 查询链路里还有典型的 N+1 和弱约束写法
6. 工程质量和测试能力离商业项目差距很大

这意味着：

> 你现在最不该做的事情，是继续兴奋地加新功能。
>
> 你最该做的事情，是先把底层红线问题修掉。

---

## 2. 这份文档怎么用

这份文档不是让你“一口气全部做完”的。

正确用法是：

1. 先看 `P0` 红线问题
2. `P0` 不解决，不建议继续加新业务功能
3. 再做 `P1` 架构整改
4. 最后做 `P2` 工程与治理提升

每一个问题我都会写：

- 现在的问题是什么
- 代码证据在哪里
- 为什么这不是商业项目可接受的写法
- 正确目标应该是什么
- 你应该一步一步怎么改
- 做完后怎么验收
- 你能从中学到什么

---

## 3. 项目当前成熟度评估

### 3.1 业务正确性：`C`

优点：

- 主要业务链路已经跑通
- 商品、库存、订单、购物车这些主域已经基本成型

问题：

- 用户端订单访问控制存在重大风险
- 支付与回调的严谨度不够
- 订单唯一号和幂等机制薄弱

### 3.2 安全性：`D`

问题：

- 支付回调未做完整验签
- 登出是假的
- 用户端和管理端鉴权标准不一致
- 参数校验层几乎没有建立

### 3.3 架构设计：`C-`

优点：

- 单体项目结构尚可理解
- 有 Redis、缓存、库存日志这些基础意识

问题：

- 没有消息队列
- WebSocket 是单机写法
- 定时任务依赖轮询
- 同步耦合严重

### 3.4 工程质量：`D`

问题：

- 测试几乎没有覆盖核心业务
- 返回模型过于粗糙
- 错误码体系缺失
- 代码层仍有较多历史包袱和弱约束写法

### 3.5 可运维性：`C`

优点：

- 已经具备 Redis、日志、部分缓存能力

问题：

- 监控、审计、回调日志、幂等记录、事件记录都不完善

---

## 4. 执行纪律

这部分很重要。

如果你真的想把项目拉到商业项目标准，建议执行下面这几条纪律：

### 4.1 先冻结新功能

在下面这些问题修完前，不建议继续加新功能：

- 订单归属校验
- 支付回调验签与幂等
- 订单号生成
- 登录/登出链路重构

### 4.2 每修一个问题，都要有验收

每项整改都应该至少留下：

- 修改文件清单
- 自测步骤
- 风险说明
- 下一步待办

### 4.3 不要为了“先能跑”牺牲边界

最典型的错误思路是：

- “先不验签，后面再补”
- “先不做归属校验，反正用户不会乱试”
- “先用时间戳顶一下订单号”

商业项目不能这么干。

---

## 5. P0 红线问题

这一节是最高优先级。

---

## 6. P0-1 用户端订单接口缺少归属校验

### 6.1 问题是什么

用户端订单接口当前很可能存在“只要知道订单 ID 或订单号，就能操作别人的订单”的风险。

### 6.2 代码证据

用户端控制器入口：

- `[controller/user/OrderController.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/controller/user/OrderController.java)`

对应服务实现：

- `[service/impl/OrderServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java)`

关键风险点：

- `show(Long id)` 直接 `orderMapper.selectbyId(id)`
- `userCancel(Long id)` 直接 `orderMapper.selectbyId(id)`
- `repetition(Long id)` 直接 `orderMapper.selectbyId(id)`
- `payment(OrdersPaymentDTO)` 直接 `orderMapper.getByNumber(orderNumber)`

Mapper 也证明了这一点：

- `[mapper/OrderMapper.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/mapper/OrderMapper.java)`

当前只有：

- `selectbyId(Long id)`
- `getByNumber(String orderNumber)`

没有：

- `selectByIdAndUserId`
- `getByNumberAndUserId`

### 6.3 为什么这是红线

这不是“优化项”，而是权限漏洞。

如果一个用户能：

- 查看别人的订单详情
- 取消别人的订单
- 对别人的订单发起支付
- 从别人的历史订单“再来一单”

那这个项目在商业上就是不合格的。

### 6.4 正确标准

用户端所有订单读取和修改，都必须同时满足：

- 当前登录用户已认证
- 订单属于当前用户

### 6.5 你应该怎么改

#### 第一步：补 Mapper 方法

在 `OrderMapper` 中新增：

- `selectByIdAndUserId(Long id, Long userId)`
- `getByNumberAndUserId(String orderNumber, Long userId)`

#### 第二步：替换用户端服务调用

在 `OrderServiceImpl` 中修改这些方法：

- `show`
- `userCancel`
- `repetition`
- `payment`

都改成基于：

- `BaseContext.getCurrentId()`
- 加 `userId` 条件查询

#### 第三步：查不到时不要返回“系统异常”

应该返回业务错误：

- “订单不存在”
- 或“无权访问该订单”

更推荐你分开：

- 不存在
- 无权限

### 6.6 验收标准

你至少要验证：

1. 用户 A 无法查看用户 B 的订单
2. 用户 A 无法取消用户 B 的订单
3. 用户 A 无法支付用户 B 的订单
4. 用户 A 无法对用户 B 的订单执行“再来一单”

### 6.7 你会学到什么

这叫：

> 对象级权限控制

很多新手只会做“有没有登录”，不会做“这个资源是不是你的”。

商业项目必须做后者。

---

## 7. P0-2 微信支付回调处理不符合商业项目标准

### 7.1 问题是什么

当前支付回调逻辑做了“解密”，但没有看到完整的签名校验和可靠幂等处理。

### 7.2 代码证据

回调入口：

- `[controller/nofity/PayNotifyController.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/controller/nofity/PayNotifyController.java)`

问题点：

1. 只读取请求体并解密
2. 没有看到对微信回调头的签名验证逻辑
3. `paySuccess(outTradeNo)` 没有做充分的幂等保护

支付成功处理：

- `[service/impl/OrderServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java)`

`paySuccess` 当前逻辑：

- 通过订单号查订单
- 直接更新为已支付
- 直接群发 WebSocket 通知

没有明显看到：

- 是否已处理过该回调
- 是否已支付过
- 是否记录原始回调事件

### 7.3 为什么这是红线

支付回调属于高风险接口。

如果这块不严谨，会出现：

- 伪造回调
- 重复回调重复处理
- 重复推送消息
- 状态错乱
- 订单已支付但处理记录丢失

### 7.4 正确标准

支付回调至少要满足：

1. 验签
2. 幂等
3. 落库记录
4. 事务处理
5. 成功后再 ACK

### 7.5 你应该怎么改

#### 第一步：补回调验签

不要只解密。

你需要校验：

- `Wechatpay-Signature`
- `Wechatpay-Timestamp`
- `Wechatpay-Nonce`
- 平台证书序列号

如果验签失败，直接拒绝。

#### 第二步：给回调做幂等

可以做两层：

- 订单层幂等：如果订单已支付，直接返回成功，不重复处理
- 回调事件层幂等：把微信回调事件 ID 或订单号 + 回调状态写入表，做唯一约束

#### 第三步：增加支付回调日志表

建议新增一张表，例如：

- `payment_callback_log`

字段建议包括：

- `id`
- `order_no`
- `event_type`
- `raw_body`
- `verify_status`
- `process_status`
- `create_time`

#### 第四步：改造 `paySuccess`

正确逻辑应该类似：

1. 查订单
2. 如果订单不存在，记录异常回调
3. 如果订单已支付，直接幂等返回
4. 如果订单未支付，更新状态
5. 记录支付成功事件
6. 再发通知事件

### 7.6 验收标准

要能验证：

1. 同一笔支付回调重复来 3 次，订单只更新一次
2. 已支付订单再次回调，不重复发通知
3. 非法签名请求不能改订单状态

### 7.7 你会学到什么

这就是：

> 支付回调不是“收到通知就改状态”，而是一个安全与幂等双重敏感点。

---

## 8. P0-3 订单号生成方案过于脆弱

### 8.1 问题是什么

当前订单号用的是 `System.currentTimeMillis()`。

### 8.2 代码证据

位置：

- `[service/impl/OrderServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java)`

出现位置包括：

- 下单时生成订单号
- 再来一单时重新生成订单号
- 退款单号也依赖时间戳拼接

数据库层：

- `[db/sky.sql](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/resources/db/sky.sql)`

`orders.number` 当前只有普通索引，没有唯一约束。

### 8.3 为什么这是红线

在并发场景下：

- 同一毫秒内完全可能有多笔订单
- 多实例部署时风险更大

这会导致：

- 订单号碰撞
- 支付单号混乱
- 回调匹配错误

### 8.4 正确标准

订单号必须：

- 全局唯一
- 高并发下稳定
- 可以跨实例使用

### 8.5 你应该怎么改

推荐方案：

- 用 Snowflake 算法
- 或 Redis 自增 + 日期前缀

第一阶段你可以这样做：

1. 新增一个 `IdGenerator` 组件
2. 专门生成：
   - 订单号
   - 退款单号
3. 给 `orders.number` 加唯一索引

### 8.6 验收标准

你应该验证：

1. 连续高频创建订单，不出现重复号
2. 数据库唯一约束生效
3. 支付和退款单号都不再依赖裸时间戳

### 8.7 你会学到什么

> 商业项目中的“唯一标识”不能靠运气。

---

## 9. P0-4 登录和登出体系不合格

### 9.1 问题是什么

当前登录链路不是“严格设计后的认证系统”，而更像“能发 JWT 就先发一个”。

### 9.2 代码证据

管理端登录：

- `[controller/admin/EmployeeController.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java)`
- `[service/impl/EmployeeServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/EmployeeServiceImpl.java)`

用户端登录：

- `[controller/user/UserController.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/controller/user/UserController.java)`
- `[service/impl/UserServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/UserServiceImpl.java)`

拦截器：

- `[interceptor/JwtTokenAdminInterceptor.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/interceptor/JwtTokenAdminInterceptor.java)`
- `[interceptor/JwtTokenUserInterceptor.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/interceptor/JwtTokenUserInterceptor.java)`

明显问题：

1. 管理端和用户端认证策略完全不统一
2. 管理端有登录失败限制，用户端几乎没有风控
3. 登出接口是假的，只是返回成功
4. JWT 没有会话管理，没有 refresh token，没有黑名单
5. 用户端 401 和管理端 401 返回格式还不一致

### 9.3 为什么这是红线

认证系统如果只是“登录时发 token，之后全靠它自己过期”，会遇到：

- 登出后 token 仍可用
- 无法踢下线
- 无法统一失效
- 无法做设备级会话管理

### 9.4 正确标准

至少要做到：

- 登录统一由 `AuthService` 负责
- access token + refresh token 分离
- logout 真正让 token 失效
- 认证失败返回统一格式
- 登录行为有审计日志

### 9.5 你应该怎么改

#### 第一步：抽认证服务

不要让 controller 自己拼 token。

建议抽一个：

- `AuthService`

负责：

- 登录校验
- token 生成
- refresh token 生成
- 登出
- token 刷新

#### 第二步：补 Redis 会话层

至少保存：

- token / sessionId
- 用户 ID
- 类型（admin/user）
- 失效时间
- 状态（active/invalid）

#### 第三步：改 logout

`logout` 不能只是 `Result.success()`。

应该做：

- 将当前 token 或 session 标记失效
- 后续请求即使 token 未过期，也拒绝访问

#### 第四步：统一 401 响应

管理端和用户端应该都返回统一 JSON：

- 错误码
- 错误消息
- 可选 traceId

#### 第五步：用户端登录也要有基础风控

哪怕不是用户名密码，也要做：

- code 非空校验
- 微信接口异常处理
- 请求频率限制
- 登录日志

### 9.6 验收标准

你至少要验证：

1. 登录成功后能正常访问
2. logout 后旧 token 不能再访问
3. 用户端和管理端的 401 返回结构一致
4. 登录失败或第三方接口失败有清晰日志

### 9.7 你会学到什么

> JWT 不是认证系统本身，它只是认证系统里的一种令牌形式。

---

## 10. P0-5 参数校验体系几乎为空

### 10.1 问题是什么

项目里几乎没有建立 Bean Validation 体系。

### 10.2 代码证据

我全项目检索后，没有看到明显的：

- `@Valid`
- `@Validated`
- `@NotBlank`
- `@NotNull`
- `@Size`

### 10.3 为什么这是红线

没有参数校验意味着：

- controller 只能手写 if 判断
- 漏校验概率极高
- 错误提示不统一

### 10.4 正确标准

DTO 层应该明确声明：

- 哪些字段必填
- 长度范围
- 数值范围
- 枚举合法性

### 10.5 你应该怎么改

第一批先改这些 DTO：

- `EmployeeLoginDTO`
- `PasswordEditDTO`
- `OrdersSubmitDTO`
- `OrdersPaymentDTO`
- `OrdersConfirmDTO`
- `OrdersRejectionDTO`
- `StockAdjustDTO`
- `UserLoginDTO`

controller 中统一用：

- `@Validated`
- `@Valid`

然后在全局异常处理器里补：

- 参数校验异常处理

### 10.6 验收标准

你要看到：

1. 空参数进入接口时，不再靠 if 手动拦
2. 错误提示统一
3. controller 代码明显变干净

---

## 11. P1 架构问题

P1 不是“今天不修明天就出事故”，但不修的话，这个项目永远脱不掉 demo 味。

---

## 12. P1-1 没有消息队列，订单链路事件化能力不足

### 12.1 现在的问题

项目里目前没有 RabbitMQ，也没有 Kafka，也没有任何真正的事件总线。

代码证据：

- `pom.xml` 里没有 MQ 依赖
- 全项目没有 `RabbitTemplate` / `@RabbitListener` / `amqp` 相关实现

### 12.2 为什么这是问题

现在很多动作都还是同步耦合：

- 支付成功后直接 WebSocket 通知
- 超时订单靠定时任务扫表
- 搜索热词没有异步统计
- 审计日志没有异步落库
- 库存预警也没有事件链路

这会导致：

- 耦合重
- 扩展难
- 多实例后行为不稳定
- 一条链路失败影响另一条链路

### 12.3 RabbitMQ 适合上在哪些地方

先说结论：

不是所有地方都该上 MQ。

#### 非常适合上 MQ 的地方

1. 订单超时取消
2. 支付成功后的后台通知
3. 搜索关键词统计
4. 审计日志
5. 库存预警通知
6. 退款结果通知

#### 不建议一上来就强行上 MQ 的地方

1. 订单主表写入
2. 订单明细写入
3. 核心库存扣减

这几项仍然优先保留本地事务，先保证强一致。

### 12.4 你可以怎么设计 RabbitMQ

建议第一版就够用了：

#### Exchange

- `order.event.exchange`
- `notify.event.exchange`
- `audit.event.exchange`
- `search.event.exchange`

#### Queue

- `order.timeout.delay.queue`
- `order.timeout.process.queue`
- `order.paid.notify.queue`
- `order.audit.queue`
- `stock.alert.queue`
- `search.keyword.queue`

### 12.5 当前项目最值得先落地的两个 MQ 场景

#### 场景 1：订单超时取消

现在做法：

- `[task/OrderTask.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/task/OrderTask.java)`
- 每分钟扫一次数据库

商业项目更推荐：

- 下单成功后发送延迟消息
- 15 分钟后进入超时处理队列
- 消费时校验状态仍为未支付再取消

这样比“全表扫 + 轮询”更稳。

#### 场景 2：支付成功后的通知和审计

现在做法：

- `paySuccess` 里直接群发 WebSocket

更合理的做法：

- `paySuccess` 只负责订单状态变更
- 然后发一个 `order.paid` 事件
- 后续消费者分别处理：
  - 管理端通知
  - 审计日志
  - 统计更新

### 12.6 你应该怎么实施

#### 第一步：先加依赖和基础配置

新增 RabbitMQ 依赖和连接配置。

#### 第二步：先只做“订单超时取消”

不要一上来做一堆。

先把：

- 订单创建 -> 发送延迟消息
- 超时消费 -> 校验状态 -> 取消订单

打通。

#### 第三步：再做“支付成功通知”

把 `paySuccess` 里直接 WebSocket 的逻辑拆出去。

### 12.7 验收标准

1. 订单超时不再主要依赖轮询扫表
2. 支付成功后通知不再耦合在支付成功事务里
3. 一个通知消费失败不影响订单支付成功本身

### 12.8 你会学到什么

> MQ 的核心价值不是“高级”，而是把同步强耦合改造成可扩展的事件链路。

---

## 13. P1-2 WebSocket 实现不符合商业项目标准

### 13.1 问题是什么

当前 WebSocket 实现是典型单机 demo 写法。

### 13.2 代码证据

位置：

- `[websocket/WebSocketServer.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/websocket/WebSocketServer.java)`

问题点：

1. `static Map<String, Session> sessionMap = new HashMap()`
2. 不是线程安全容器
3. 没有认证
4. `sid` 直接由路径带入
5. 只能单机用，多实例就失效

### 13.3 为什么这是问题

这会带来：

- 并发读写安全问题
- 任意伪造 sid
- 多节点部署后消息丢失

### 13.4 正确标准

至少应该做到：

- 用 `ConcurrentHashMap`
- 连接建立前校验身份
- session 与用户 ID/角色绑定
- 多实例场景通过 Redis/MQ 广播

### 13.5 你应该怎么改

第一阶段：

- 改成线程安全容器
- 建立连接时校验 token
- 不再直接信任路径里的 sid

第二阶段：

- WebSocket 服务只做连接管理
- 消息通过 MQ 或 Redis Pub/Sub 分发

---

## 14. P1-3 查询链路存在 N+1 和数据过滤漏洞

### 14.1 问题是什么

当前几个高频接口还有明显的查询质量问题。

### 14.2 代码证据

#### 问题 1：后台订单条件查询 N+1

位置：

- `[service/impl/OrderServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java)`

`conditionSearch` 中：

- 先查订单分页
- 再对每条订单单独 `selectByOrderId`

这是典型 N+1。

#### 问题 2：商品列表 N+1

位置：

- `[service/impl/ProductServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/ProductServiceImpl.java)`

`listWithFlavor` 和 `search` 中：

- 每个商品都单独查规格
- 每个商品都单独查适配机型

#### 问题 3：用户端商品列表没有按状态过滤

位置：

- `[controller/user/ProductController.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/controller/user/ProductController.java)`
- `[mapper/ProductMapper.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/mapper/ProductMapper.java)`

控制器里设置了：

- `product.setStatus(StatusConstant.ENABLE)`

但 Mapper 的 `selectByCategoryId(Long categoryId)` 实际只按分类查，没有按状态过滤。

这意味着：

- 下架商品有可能被用户端查出来

### 14.3 为什么这是问题

这会造成：

- 数据量一上来接口变慢
- 商品数据可能越权暴露

### 14.4 你应该怎么改

#### 订单分页

参照你历史订单分页的写法，改为：

1. 先分页查订单
2. 收集订单 ID 列表
3. 批量查明细
4. 按 orderId 分组组装

#### 商品列表和搜索

补批量查询：

- `selectByProductIds`
- `selectPhoneModelsByProductIds`

避免对每个商品单独查一次。

#### 商品状态过滤

把 `selectByCategoryId` 改成真正按：

- `category_id`
- `status = 1`

一起查。

#### 搜索分页

当前搜索没有分页。

商业项目不建议长期保留“无分页模糊搜索”。

建议补：

- `page`
- `pageSize`

---

## 15. P1-4 异常体系和返回模型太弱

### 15.1 问题是什么

当前返回模型过于粗糙。

### 15.2 代码证据

返回模型：

- `[result/Result.java](/F:/java/dlivery-project/sky-take-out/sky-common/src/main/java/com/sky/result/Result.java)`

只有：

- `code`
- `msg`
- `data`

而且 `code` 基本只有：

- `1`
- `0`

异常处理：

- `[handler/GlobalExceptionHandler.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/handler/GlobalExceptionHandler.java)`

拦截器返回也不统一：

- admin 401 返回 JSON
- user 401 只返回状态码

### 15.3 为什么这是问题

商业项目里，前后端协作需要：

- 可识别错误类型
- 可定位问题
- 可扩展

只有 `0/1` 根本不够。

### 15.4 正确标准

建议返回结构至少包含：

- `code`
- `message`
- `data`
- `traceId`
- `timestamp`

错误码要能区分：

- 参数错误
- 未登录
- 无权限
- 业务冲突
- 系统异常

### 15.5 你应该怎么改

第一阶段：

- 设计错误码枚举
- 改造 `Result`
- 统一拦截器 401 返回
- 全局异常返回带业务码

第二阶段：

- 补 traceId
- 日志中打印 traceId

---

## 16. P1-5 定时任务方案不适合未来扩容

### 16.1 问题是什么

订单超时现在靠定时任务轮询数据库。

### 16.2 代码证据

位置：

- `[task/OrderTask.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/task/OrderTask.java)`

逻辑：

- 每分钟跑一次
- 查所有超时未支付订单
- 逐单取消
- 逐单归还库存

### 16.3 为什么这是问题

单机阶段能用，但后面会出现：

- 扫表压力
- 多实例重复执行
- 无分布式锁

### 16.4 正确方向

长期：

- MQ 延迟消息

如果暂时不上 MQ，至少也要：

- 做分布式锁
- 缩小扫描范围
- 保证幂等

---

## 17. P1-6 登录外部依赖处理太薄

### 17.1 问题是什么

用户端登录直接同步调用微信接口，错误处理和韧性设计都偏弱。

### 17.2 代码证据

位置：

- `[service/impl/UserServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/UserServiceImpl.java)`

逻辑：

- 直接 `HttpClientUtil.doGet`
- 没看到超时、重试、异常分类、降级、调用日志结构化记录

### 17.3 为什么这是问题

外部依赖不可控。

如果微信接口短时波动，会出现：

- 登录失败
- 错误信息不可定位
- 用户体验差

### 17.4 你应该怎么改

第一阶段：

- 明确超时时间
- 捕获网络异常
- 记录外部调用耗时与结果码
- code 为空直接拒绝

第二阶段：

- 封装第三方客户端层
- 不要让 Service 直接拼 HTTP 调用

---

## 18. P2 工程和治理问题

这些不一定今天出事故，但不处理的话，项目会越来越难维护。

---

## 19. P2-1 测试覆盖严重不足

### 19.1 现状

当前我检索到的测试主要是：

- `BCryptTest`
- `AutoFillAspectTest`
- `HttpClientTest`
- `RedisTest`

没有看到成体系的：

- Controller 测试
- Service 测试
- 订单测试
- 支付测试
- 权限测试

### 19.2 为什么这是问题

没有自动化测试，你每改一次核心链路都在赌。

### 19.3 你应该怎么补

第一批优先补：

1. 订单归属校验测试
2. 支付回调幂等测试
3. 登录失败限制测试
4. 商品列表状态过滤测试
5. 搜索分页与关键字测试

### 19.4 学习重点

不要一上来全写集成测试。

先学会写：

- Service 层单元测试
- Mapper 层 SQL 行为测试
- Controller 层接口测试

---

## 20. P2-2 包结构与命名治理还不够干净

### 20.1 现状

还存在一些明显不够严谨的痕迹，例如：

- `controller.nofity` 包名拼写错误
- `sky-take-out` 根命名仍然是历史名称
- 注释里还有一部分“兼容旧模式/已移除”的历史说明

### 20.2 为什么要管这件事

命名治理看起来不如功能重要，但它直接影响：

- 新人理解项目速度
- 后续重构成本
- 是否容易继续引入旧语义

### 20.3 你应该怎么做

这一项放在 P2 做：

- 包名修正
- 术语统一
- 删除过时注释
- 更新文档与类名

---

## 21. P2-3 依赖栈和基础设施还偏旧

这部分你前面已经知道了，我这里只保留商业项目视角的结论：

- 依赖过旧不是“立刻不能跑”
- 但它会影响：
  - 安全性
  - 社区支持
  - 新特性接入

这项建议放在你修完 P0、P1 之后再做，不要现在和核心整改混在一起。

---

## 22. 建议的整改顺序

这是我最推荐的执行顺序。

### 第一阶段：必须马上做

1. 订单归属校验
2. 支付回调验签
3. 支付回调幂等
4. 订单号生成器
5. logout 真失效
6. 参数校验体系第一版

### 第二阶段：架构升级

1. 订单超时从轮询过渡到 MQ
2. 支付成功通知事件化
3. WebSocket 连接认证
4. 搜索和商品列表批量查询优化
5. 错误码体系

### 第三阶段：工程补强

1. 测试补齐
2. 登录审计日志
3. 回调日志表
4. 操作日志
5. 命名与包结构整理

---

## 23. 每个阶段的验收目标

### 第一阶段完成后

你应该能说：

- 用户再也不能操作别人的订单
- 支付回调不会被随便伪造
- 同一回调不会重复处理
- 订单号不会再靠毫秒撞运气

### 第二阶段完成后

你应该能说：

- 订单链路已经开始事件化
- 超时取消不再强依赖扫表
- 查询性能更可控
- 鉴权和错误返回更统一

### 第三阶段完成后

你应该能说：

- 这个项目开始像一个能长期维护的项目
- 而不是“只有作者自己看得懂的版本”

---

## 24. 对新人的学习建议

这部分专门写给你。

你现在最值得学的，不是某个框架 API，而是这 6 个思维：

### 24.1 资源归属思维

“你登录了”不等于“你有权操作这条数据”。

### 24.2 幂等思维

“外部通知来了”不等于“我就一定要再处理一遍”。

### 24.3 事件化思维

“业务成功了”不等于“所有后续动作都要同步完成”。

### 24.4 边界思维

控制器、服务、Mapper、第三方客户端要各司其职。

### 24.5 一致性思维

什么必须同步保证正确，什么可以异步最终一致，要分清。

### 24.6 验收思维

每改一个点，都要能回答：

1. 我修了什么问题？
2. 我怎么证明它修好了？
3. 如果以后被改坏，我怎么第一时间发现？

---

## 25. 最终判断

如果你问我一句最直白的话：

> 这个项目离“商业项目标准”还有多远？

我的回答是：

- 不远到需要推倒重来
- 但也绝对不到“再加点功能就能上线放心用”的程度

它现在最需要的，不是更多页面，不是更多炫功能，而是：

1. 把权限边界补完整
2. 把支付和订单流转做严谨
3. 把同步耦合改成事件化
4. 把查询和错误体系做规范
5. 把测试和验收补上

当你把这些做完，这个项目才真正开始从“改造过的课程项目”向“可上线的商业项目”转变。
