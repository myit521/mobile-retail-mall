# 项目审查记录（2026-10-10）

范围：订单、支付、退款、库存及管理端订单详情。下表记录发现时的代码证据；当前修复状态与验证结果见文末。

| ID | 优先级 | 问题 | 证据与影响 | 建议 |
| --- | --- | --- | --- | --- |
| R-01 | 严重 | 下单金额由客户端指定 | `OrderServiceImpl.createOrder()` 将 `OrdersSubmitDTO.amount` 直接复制进订单；`payment()` 使用订单金额创建预支付单。用户可提交低于购物车总价的金额。 | 服务端按商品价格和数量计算金额，校验价格与商品状态。 |
| R-02 | 高 | 退款与订单事务不一致 | `userCancel()`、`rejection()`、`adminCancel()` 在数据库事务内先调用 `performRefund()`，后更新订单和归还库存。后续失败会回滚数据库，无法回滚外部退款；重试又生成新退款单号。 | 持久化退款请求及固定幂等单号，事务提交后执行并按回调更新结果。 |
| R-03 | 高 | 库存绝对值更新覆盖并发变动 | `StockServiceImpl.adjustStock()` 和 `confirmCheckRecord()` 先读库存，再调用 `InventoryProductMapper.updateStock()` 写绝对值。并发订单扣减或其他调整可能丢失。 | 使用带条件的原子更新或行锁，记录实际更新前后值。 |
| R-04 | 中 | 管理端订单详情误用用户查询 | 管理端 `OrderController.details()` 调用 `OrderServiceImpl.show()`；后者按当前 ID 作为用户 ID 过滤。管理员无法查看其他用户订单。 | 提供独立的管理端详情查询，不以员工 ID 过滤用户订单。 |
| R-05 | 中 | 盘点确认缺少原子状态转换 | `confirmCheckRecord()` 先读取待确认状态；`StockCheckRecordMapper.update()` 仅按 ID 更新。两个并发确认可同时写库存日志。 | 状态更新加 `status = PENDING` 条件，检查受影响行数，并在同一事务内调整库存。 |
| R-06 | 高 | 订单超时兜底任务没有执行扫描 | `OrderTask.processTimeoutOrder()` 只写日志；下单时通过 `OrderEventPublisher.publishOrderTimeout()` 发送 MQ 消息，但没有等待发布确认。消息丢失或无法路由后，待支付订单不会被兜底取消，库存会持续占用。 | 实现定时查询超时待支付订单并逐单条件取消；或将超时任务持久化并确认投递。 |
| R-07 | 高 | 退款 HTTP 错误响应被当作成功 | `WeChatPayUtil.post()` 直接返回响应体，不检查 HTTP 状态；`performRefund()` 不解析退款结果，取消/拒单路径随后标记 `REFUND` 并归还库存。微信拒绝退款时，本地仍显示已退款。 | 检查 HTTP 状态与退款响应，持久化处理中状态，并以退款成功回调或主动查询确认最终状态。 |
| R-08 | 高 | 支付回调晚于本地超时会形成已收款的取消订单 | 超时消费调用 `cancelTimeoutOrder()`，将待支付订单取消并归还库存；稍后到达的已验签支付回调在 `applyPayment()` 因状态非待支付而失败，接口持续返回 500。即使用户在截止前付款，回调延迟也可触发该顺序。 | 超时取消前查询/关闭微信交易；对已收款但已取消的回调建立补偿退款与人工核对路径。 |
| R-09 | 高 | 重复提交可从同一购物车生成多个订单 | `submit()` 通过普通查询读取购物车，分别插入订单和扣库存，最后 `clean()` 无条件按用户删除购物车；没有请求幂等键或按用户串行化。同一用户并发提交可均读到相同购物车并生成重复订单。 | 为提交请求设置持久化幂等键，或在事务中锁定并原子消费购物车。 |
| R-10 | 高 | 盘点可将商品库存设为负数 | `StockCheckRecordDTO.actualStock` 没有非负校验，`StockController.submitCheckRecord()` 没有 `@Valid`；`confirmCheckRecord()` 直接把该数写入 `product.stock`。提交负数并确认后，库存成为负数。 | 在请求与服务层校验实盘库存非负，数据库增加约束，并使用条件更新。 |
| R-11 | 高 | 默认 JWT 密钥与会话身份未绑定 | `application.yml` 为管理员和用户 JWT 提供公开默认密钥；管理员拦截器信任 token 中的员工 ID 与角色，而 Redis 校验只检查 `sessionId` 是否存在。部署漏配密钥时，普通员工可用自己的有效会话 ID 签发 `ADMIN` token。 | 缺密钥时启动失败；校验 Redis 会话中的员工 ID 与角色并处理角色变更。 |
| R-12 | 高 | 管理员与用户备忘录共用 ID 命名空间 | 两端 `MemoController` 共用 `MemoServiceImpl`，都把 `BaseContext.currentId` 写入和查询 `memo.user_id`。员工 ID 与用户 ID 相等时，双方可以读写彼此的备忘录。 | 存储并查询 `owner_type` 与 `owner_id`；迁移已有数据并区分两类接口。 |
| R-13 | 高 | 报表日期范围无上限 | 营业额和订单统计按天循环，每天分别执行数据库查询。已登录员工可提交跨越数千年的日期范围，长时间占用请求线程和数据库连接。 | 限制日期跨度与顺序，优先改为一次聚合查询。 |
| R-14 | 高 | 管理端库存调整请求字段不匹配 | 管理端发 `changeQuantity`，后端 `StockAdjustDTO` 必填字段为 `quantity`，因此正常点击“确定”即收到 400，库存调整不可用。 | 对齐字段名并添加前后端契约测试。 |
| R-15 | 高 | 管理端刷新后被登出 | 登录把用户信息写入 cookie `user_info`，恢复登录态的 `getUserInfo()` 却读取 `userInfo`；刷新后角色数组为空，恢复失败并清除 token。 | 统一 cookie key 和序列化方式，补首屏恢复测试。 |
| R-16 | 中 | 前端丢失后端业务错误信息 | 后端业务错误使用非 2xx HTTP 与 `ErrorResponse.message`；管理端响应拦截器只在 2xx 中读取 `msg`，错误分支不读取 `error.response.data`。库存不足等原因只显示为通用 HTTP 错误。 | 统一错误响应契约，并在非 2xx 分支解析展示服务端消息。 |
| R-17 | 中 | 重复请求取消后 Promise 永不结束 | 管理端请求拦截器取消重复请求；响应拦截器对取消返回 `new Promise(() => {})`。调用方的等待和 loading 状态会永久挂起。 | 返回可识别的拒绝结果并在调用方清理 loading。 |
| R-18 | 中 | 小程序 401 后无法按提示自动重登 | 请求层清除 token 后仅切换首页；`App.vue.autoLogin()` 只在 `onLaunch` 调用，切换 Tab 不会执行登录。后续请求继续 401，直到重启小程序。 | 将自动登录封装为可重入动作，在 401 恢复路径显式调用。 |
| R-19 | 中 | 库存列表的低库存提示无法显示 | 管理端 `isLowStock` 依赖 `row.alertThreshold`，但 `ProductVO` 和商品分页查询均不返回该字段，低库存提示恒为 false。 | 商品列表响应补齐预警阈值，或使用库存专用 DTO。 |
| R-20 | 中 | 库存日志按名称搜索被静默忽略 | 管理端发送 `productName`，后端查询 DTO 和 Mapper 只支持 `productId`。页面看似筛选，实际返回未按名称过滤的日志。 | 对齐前后端字段并验证筛选结果。 |
| R-21 | 中 | 用户备忘录提醒无法投递且会被标记已提醒 | `MemoTask` 按备忘录的用户 ID 调用只支持管理员会话的 WebSocket 通道，随后不检查投递结果就将记录标记已提醒。C 端提醒会静默丢失。 | 增加用户通知通道或持久化站内消息；只在确认投递后标记完成。 |
| R-22 | 中 | 编辑员工时省略角色会将其降为普通员工 | `EmployeeServiceImpl.update()` 对空角色执行 `normalizeRole(null) -> EMPLOYEE`，然后 Mapper 写回；只改手机号等字段的请求也会覆盖原管理员角色。 | 更新时仅在明确提供角色时变更角色，并对角色变更单独授权。 |
| R-23 | 中 | 本地环境文件未被 Git 忽略 | 当前 `sky-take-out/.env` 为未跟踪文件，`sky-take-out/.gitignore` 未排除它。该文件属于环境配置，后续执行 `git add -A` 会把它纳入提交。 | 将 `.env` 加入忽略规则，保留无凭据的 `.env.example`；提交前检查暂存内容。 |

## 代码定位

- R-01：`sky-pojo/src/main/java/com/sky/dto/OrdersSubmitDTO.java:20`；`sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:122,547`
- R-02：`sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:226,245,397,412,437,457,485`
- R-03：`sky-server/src/main/java/com/sky/inventory/internal/application/StockServiceImpl.java:172,183,263,271`；`sky-server/src/main/java/com/sky/inventory/internal/persistence/InventoryProductMapper.java:39`
- R-04：`sky-server/src/main/java/com/sky/order/internal/controller/admin/OrderController.java:56`；`sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:189`
- R-05：`sky-server/src/main/java/com/sky/inventory/internal/application/StockServiceImpl.java:254,293`；`sky-server/src/main/resources/mapper/StockCheckRecordMapper.xml:13`
- R-06：`sky-server/src/main/java/com/sky/order/internal/task/OrderTask.java:14`；`sky-server/src/main/java/com/sky/order/internal/messaging/OrderEventPublisher.java:18`
- R-07：`sky-common/src/main/java/com/sky/utils/WeChatPayUtil.java:82,91,93,217`；`sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:493`
- R-08：`sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:589,600,623,634`
- R-09：`sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:73,77,81,88,91`；`sky-server/src/main/java/com/sky/product/internal/persistence/ShoppingCartMapper.java:33`
- R-10：`sky-pojo/src/main/java/com/sky/dto/StockCheckRecordDTO.java:20`；`sky-server/src/main/java/com/sky/inventory/internal/controller/StockController.java:108`；`sky-server/src/main/java/com/sky/inventory/internal/application/StockServiceImpl.java:271`
- R-11：`sky-server/src/main/resources/application.yml:90,97`；`sky-server/src/main/java/com/sky/interceptor/JwtTokenAdminInterceptor.java:56`；`sky-server/src/main/java/com/sky/auth/internal/application/TokenSessionServiceImpl.java:111`
- R-12：`sky-server/src/main/java/com/sky/service/impl/MemoServiceImpl.java:51,114,183`；`sky-server/src/main/resources/mapper/MemoMapper.xml:57`
- R-13：`sky-server/src/main/java/com/sky/service/impl/ReportServiceImpl.java:147,153,192,198,201`
- R-14：`../project-sky-admin-vue-ts/src/views/stock/index.vue:274`；`sky-pojo/src/main/java/com/sky/dto/StockAdjustDTO.java:21`
- R-15：`../project-sky-admin-vue-ts/src/store/modules/user.ts:78,98`；`../project-sky-admin-vue-ts/src/utils/cookies.ts:22`；`../project-sky-admin-vue-ts/src/permission.ts:13`
- R-16：`sky-server/src/main/java/com/sky/handler/GlobalExceptionHandler.java:29`；`sky-common/src/main/java/com/sky/result/ErrorResponse.java:10`；`../project-sky-admin-vue-ts/src/utils/request.ts:101,107`
- R-17：`../project-sky-admin-vue-ts/src/utils/request.ts:62,107`
- R-18：`../project-rjwm-weixin-uniapp-develop-wsy/utils/request.js:24`；`../project-rjwm-weixin-uniapp-develop-wsy/App.vue:6`
- R-19：`../project-sky-admin-vue-ts/src/views/stock/index.vue:207`；`sky-pojo/src/main/java/com/sky/vo/ProductVO.java:15`；`sky-server/src/main/resources/mapper/ProductMapper.xml:117`
- R-20：`../project-sky-admin-vue-ts/src/views/stock/index.vue:244`；`sky-pojo/src/main/java/com/sky/dto/StockLogPageQueryDTO.java:15`；`sky-server/src/main/resources/mapper/StockLogMapper.xml:21`
- R-21：`sky-server/src/main/java/com/sky/task/MemoTask.java:65,79`；`sky-server/src/main/java/com/sky/notification/internal/websocket/WebSocketTicketService.java:16,56`
- R-22：`sky-server/src/main/java/com/sky/auth/internal/application/EmployeeServiceImpl.java:147,151,162`
- R-23：`.gitignore:1`；`.env` 当前为未跟踪文件（未读取或记录其中的值）

## 审查与验证边界

- 核心订单、支付、退款和库存链路由 Codex 审查；CodeBuddy 审查其他后端模块；DeepSeek Harness CLI 审查两个前端、CI 与接口契约。外部分派报告中的重点发现已由 Codex 逐条检查相应代码路径，未直接采纳其优先级与未经证实的结论。
- 以上问题是静态代码与 SQL 路径确认；并发、微信支付与 RabbitMQ 故障场景尚未在真实中间件环境复现。审查过程中没有修改业务代码，也没有执行会写入数据库或外部服务的命令。
- 角色授权矩阵尚无明确业务要求，因此“所有管理端写接口都应仅限 ADMIN”只列为待确认策略，不作为已确认缺陷。停用页面中的 `eval`、需要攻击者先写入 Redis 才可触发的反序列化风险也暂未列为可直接触发的问题。

验证记录：`mvn -B -f sky-take-out/pom.xml -DskipITs test` 执行 171 个测试，170 个通过；`SkyApplicationSmokeTest` 因本机无可用 Docker 环境报错。`-DskipITs` 未排除该命名为 `*Test` 的 Testcontainers 测试。

审查已覆盖上述分工；修复进展与各轮验证结果见下文。

## 修复进展（2026-10-11）

| 编号 | 当前状态 | 主要改动 |
| --- | --- | --- |
| R-01 | 已实现 | 服务端根据有效商品价格重算订单金额并拒绝不一致金额。 |
| R-02、R-07 | 已实现，待真实支付联调 | 退款请求与订单事务一同持久化，使用固定退款单号；提交后发起退款，定时查询与验签回调确认最终状态，只有成功才更新本地退款状态。微信 HTTP 非成功响应作为错误处理。 |
| R-03、R-05、R-10 | 已实现，数据库集成测试通过 | 库存使用条件原子更新，盘点确认使用状态条件更新，非负校验落到请求、服务与数据库约束。2026-10-12 已验证调整与扣减并发、重复盘点确认和负值约束。 |
| R-04、R-06 | 已实现 | 管理端详情不使用用户所有权过滤；超时任务执行扫描并逐单取消。 |
| R-08 | 已实现，待真实支付联调 | 超时取消先查询并关闭微信订单；已支付或迟到支付走回调处理；支付与取消条件更新竞争时锁定重读，已取消订单记录补偿退款。 |
| R-09 | 已实现，数据库集成测试通过 | 订单提交与购物车变更按用户行锁串行化，提交后消费购物车。2026-10-12 已验证并发下单只生成一单、下单期间新增购物车商品不会丢失。 |
| R-11 | 已实现 | 缺 JWT 密钥时启动失败；HTTP 和 WebSocket 会话核对 Redis 身份、角色及当前员工状态。 |
| R-12 | 新数据已修复；历史数据保留隔离 | 备忘录加入 owner_type，查询按管理员或用户隔离。用户确认目前没有可信的历史归属来源，旧记录保持 owner_type 为 NULL，不向任何一端展示，不进行猜测性回填。 |
| R-13 | 已实现 | 报表校验日期顺序与跨度。 |
| R-14～R-20 | 已实现 | 对齐库存调整与日志筛选契约，修复管理端登录恢复、错误显示、取消请求结算、小程序自动重登和低库存阈值展示。 |
| R-21 | 已实现 | 用户提醒持久化为站内消息，管理端 WebSocket 仅在实际投递时标记成功。 |
| R-22、R-23 | 已实现 | 未提供角色时保持现有角色；环境文件加入 Git 忽略规则。 |

验证：Maven 测试代码编译通过；订单、支付、退款、库存、鉴权、备忘录和模块边界定向测试通过，新增支付与超时取消竞争的回归测试通过。使用测试专用 JWT 密钥，执行非 Docker 自动化测试 210 个，0 失败、0 错误。管理端完整 Jest 套件 6 组共 16 个测试通过，生产构建通过（仅有既有资源体积警告）；小程序 2 个 Node 契约测试通过；`git diff --check` 通过。

限制：本机 Docker daemon 不可用，依赖 Testcontainers 的库存、订单和支付并发集成测试未能运行；微信支付、RabbitMQ 故障和数据库迁移尚未在真实中间件环境联调。上述 210 个测试按 `*Test` 运行，排除了需 Docker 的 `SkyApplicationSmokeTest`、依赖本地服务的手工示例 `HttpClientTest`、`RedisTest`，以及需完整运行环境的压力测试 `OrderNumberGeneratorTest`。独立运行 `*IT` 的验证仍待 Docker 可用。

### 历史备忘录回填条件

用户于 2026-10-11 确认目前没有可信的历史归属来源，决定保留隔离状态。`V7__memo_owner_type.sql` 将历史备忘录的 `owner_type` 留空，避免员工 ID 与用户 ID 重叠时误放给另一方。若以后找到独立来源，可先用 `SELECT id, user_id FROM memo WHERE owner_type IS NULL ORDER BY id` 导出待核定列表；只有逐条确认 `ADMIN` 或 `USER` 后，才能按备忘录 `id` 精确回填。无法确认的记录继续保留 `NULL`，不得只凭 `user_id` 猜测。回填前后记录 `NULL` 行数并核对两端接口可见性。

## 集成验收（2026-10-12）

Docker Desktop 已启动。普通沙箱进程无法访问 Docker 命名管道，使用本机授权权限执行 Testcontainers 测试。首次运行发现 `target/classes` 残留旧名 `V6__memo_owner_type.sql`，与当前 V6 重号；执行 Maven `clean` 后移除旧构建产物，源码迁移版本没有冲突。

本轮修正了三个测试配置问题：Flyway 断言更新到 V8；支付回调测试通过接口接收事务代理；订单取消测试以数据库持久化结果验证退款请求，不再错误要求 `afterCommit` 回调内事务上下文已清理。

集成测试还发现：已支付订单收到超时消息时仍调用微信查单，外部查询失败会使本应结束的消费反复失败。超时处理现对本地已进入正常后续状态的订单直接返回；待支付和已取消订单继续核对微信交易，保留迟到支付补偿路径。原先失败的 `OrderEventConsumerIT.timeoutAfterPaymentDoesNotCancelPaidOrder` 已通过。

最终验证：

- `mvn -B -f sky-take-out/pom.xml verify`：BUILD SUCCESS，Surefire 200 个、Failsafe 36 个测试，均为 0 失败、0 错误、0 跳过。沿用 POM 对六个手工/历史示例测试的排除规则。
- 数据库：空库与旧版结构升级到 V8、重复迁移无操作、历史备忘录仍为 NULL 归属；库存调整与下单扣减均保留、重复盘点确认只生效一次、数据库拒绝负库存；并发下单只消费一次购物车，下单期间新增商品得以保留。
- 订单与支付：取消并发条件更新、重复支付回调、回滚后重试、迟到支付退款意图、退款请求 PROCESSING 状态持久化均通过。微信调用在这些测试中使用替身，不代表真实商户联调已完成。
- RabbitMQ：真实容器中的消息去重、毒消息重试及死信、不可路由消息退回与持久化退避、发布者重启后恢复、并发发布领取，以及 broker 停止/恢复时 readiness 状态变化均通过。
- 管理端：`node node_modules/@vue/cli-service/bin/vue-cli-service.js test:unit --runInBand`，6 组 16 个测试通过；`node --openssl-legacy-provider node_modules/@vue/cli-service/bin/vue-cli-service.js build` 成功，保留既有资源体积与 Browserslist 数据陈旧警告。当前 Node 24 直接运行旧 Webpack 4 构建会出现 OpenSSL 算法兼容错误，验证命令使用进程级兼容参数。
- 小程序：`node --test tests/app-login.contract.test.cjs tests/request-auth.contract.test.cjs`，2 个测试通过；Git 差异空白检查通过。

独立只读复核未发现阻塞问题。复核指出：库存调整与扣减测试同步了操作起点，但未强制每一种读写交错，因此它不能单独证明所有并发交错均已覆盖；双重盘点确认与购物车测试则分别通过屏障、提交前阻塞控制竞争时序。

上线前待验：

1. 用户于 2026-10-12 确认暂时没有专用微信支付联调环境，真实商户的查单、关单、签名回调和退款对账保留为上线前待验项。
2. 迁移已对受控测试数据验证，尚未在实际部署数据库快照上演练。执行 V6 前先排查 `product.stock < 0` 与 `stock_check_record.actual_stock < 0` 的历史记录；有异常时应核实并修正来源，不自动覆盖历史库存数据。
3. 历史备忘录继续保持隔离，只有出现可逐条核定的可信来源后才回填归属。

## 本地提交整理（2026-10-13）

- `464fd83`：后端订单、支付退款、库存、鉴权、备忘录修复及回归测试。
- `5d46641`：管理端与小程序登录恢复、错误处理、库存接口契约及回归测试。

提交位于 `codex/enterprise-project-hardening`，未推送。首页样式、OpenSpec 工件及原有手工测试示例等范围外内容保留在工作区，未纳入上述提交。
