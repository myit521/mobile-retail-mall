# 无聊天上下文的独立审查（2026-10-13）

审查提交：`f31142325dd02660437fdc864fb39d7349e4f79f`。主审查子 agent 使用 `fork_turns=none` 启动，没有继承此前聊天记录，没有读取历史审查报告。它分派两个只读子审查者，随后本人重读相关代码、逐条核实结论。Codex 主进程在收到完整报告后核对了登录限流、微信用户创建、位置 DTO、重复购买导航和销售额 SQL 的关键证据。

覆盖范围：模块化 Spring Boot 后端的鉴权、商品、购物车、库存、订单、支付回调、退款、Outbox/消息消费、WebSocket、备忘录、报表、Flyway 迁移，以及 Vue 管理端和 uni-app 小程序。未提交的首页样式、OpenSpec 工件与历史手工测试示例不属于审查版本。

## 确认的问题

以下为独立审查时的新发现，共 1 项 P1、8 项 P2。2026-10-14 已完成代码修复，验收与上线边界见文末。

| ID | 优先级 | 问题及可触发条件 | 影响 | 代码证据 |
| --- | --- | --- | --- | --- |
| R-30 | P1 | `admin` 登录失败锁定后，用 `admin ` 等尾空格变体继续登录。用户名数据库比较使用 utf8mb3_bin PAD SPACE 等价，Redis 限流键却拼接原始输入。 | 同一真实账号可以使用多个失败计数/锁键，绕过登录锁定并持续猜测密码。 | `sky-server/src/main/java/com/sky/auth/internal/application/EmployeeServiceImpl.java:49-56`；`LoginAttemptServiceImpl.java:36,49`；`auth/internal/persistence/EmployeeMapper.java:22`；`resources/db/migration/V1__baseline_schema.sql:22,35` |
| R-31 | P2 | 同一新微信身份使用两个有效授权码并发首次登录，两个请求查询不到用户后分别插入。openid 只有普通索引。 | 产生重复 userId，后续返回单对象的查询多行并触发 TooManyResultsException，阻断登录。 | `sky-server/src/main/java/com/sky/auth/internal/application/UserServiceImpl.java:37-45`；`auth/internal/persistence/UserMapper.java:17`；`resources/db/migration/V1__baseline_schema.sql:309` |
| R-32 | P2 | 系统库存 10、实盘 12；同一 planId/productId 在确认前提交两次，再分别确认独立记录。 | 两条 +2 差额均应用，库存变成 14；记录级 CAS 不防止同一计划商品重复提交。 | `sky-server/src/main/java/com/sky/inventory/internal/application/StockServiceImpl.java:242-251,280-283`；`resources/db/migration/V1__baseline_schema.sql:260-275` |
| R-33 | P2 | 已有商品的黑色规格，再次选择白色规格加购。代码按 productId 判断购物车已有商品后，跳过本次规格写入。 | 本次选择被旧字段或空字符串代替，加入的规格与用户选择不符。 | `../project-rjwm-weixin-uniapp-develop-wsy/pages/index/index.js:408-425` |
| R-34 | P2 | 购物车已有商品 P 的黑色规格，add/sub 请求只传 productId，省略 specInfo。SQL 省略整个规格条件后取首行。 | 默认规格操作增加、减少或删除其他规格；restore 的 null 规格也可能匹配错误行。 | `sky-server/src/main/resources/mapper/ShoppingCartMapper.xml:58-60`；`sky-server/src/main/java/com/sky/product/internal/application/ShoppingCartServiceImpl.java:46,59,112` |
| R-35 | P2 | 型号列表中 iPhone 15 位于 iPhone 15 Pro 前，设备报告 iPhone 15 Pro；前缀条件配合 find 返回较短型号。 | 加载错误型号的推荐，可能购买不兼容配件。 | `../project-rjwm-weixin-uniapp-develop-wsy/pages/phoneModel/index.vue:194,202-210` |
| R-36 | P2 | 商品位置表单提交 locationCode,row,col，后端只接收 positionCode,layerNum，并忽略未知字段。 | 保存成功却丢失位置/层数输入；列表和编辑回填同样错字段。 | `../project-sky-admin-vue-ts/src/views/productLocation/index.vue:90-95,185`；`src/views/product/addProduct.vue:156-160,260-274`；`sky-pojo/src/main/java/com/sky/dto/ProductLocationDTO.java:20-23` |
| R-37 | P2 | 再来一单接口成功后，用 uni.redirectTo 跳转注册为 tabBar 的 /pages/index/index。 | 购物车已恢复，页面却无法进入商城且没有导航失败提示。 | `../project-rjwm-weixin-uniapp-develop-wsy/pages/details/index.js:129-130`；`pages.json:116` |
| R-38 | P2 | 完成订单明细数量为 3、单价为 100；销量排行按 SUM(od.amount) 聚合。 | 销售额算成 100 而非 300；可由历史或导入的完成订单触发。 | `sky-server/src/main/resources/mapper/ReportMapper.xml:13`；`sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:174` |

## 建议修复方向

1. R-30：账号查询与登录限流使用同一规范用户名，或拒绝非规范输入。数据库尾空格规则依据 [MySQL 官方比较规则](https://dev.mysql.com/doc/refman/8.0/en/charset-binary-collations.html)。
2. R-31：openid 加唯一约束，原子创建并在重复键冲突后读取已有用户；迁移前核查已有重复身份。
3. R-32：对计划和商品设唯一约束，明确待确认记录能否更新，已确认后不可重新创建差额。
4. R-33：规格弹窗提交以本次选择生成请求，购物车数量加减则以对应行规格生成请求。
5. R-34：列表查询与商品规格精确查询分开；默认规格也必须精确比较，统一 null/空字符串表示。
6. R-35：先全列表精确匹配，再处理设备附加文本，避免将 Pro/Max/Plus 当作可忽略后缀。
7. R-36：表单、回填、列表统一采用 positionCode,layerNum；独立行列若为业务需求，需要同步后端与数据库契约。
8. R-37：使用 uni.switchTab。依据 [uni-app 官方路由文档](https://uniapp.dcloud.net.cn/api/router.html)。
9. R-38：销售额采用 SUM(od.amount * od.number)，补多件商品的统计回归测试。

## 独立验证证据

子 agent 在 sky-take-out 目录执行：

```powershell
mvn -o -pl sky-server -am '-Dtest=RefundServiceTest,RefundSettlementServiceTest,OrderSubmissionTest,OrderServiceTransitionTest,LatePaymentRaceTest,PaymentCallbackServiceTest,OutboxPublisherTest,MessageConsumptionServiceTest,SecurityReviewRegressionTest,StockServiceImplTest,ShoppingCartMutationLockTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

实际结果：65 个测试，0 失败、0 错误、0 跳过，BUILD SUCCESS；另执行两个小程序 Node 契约测试，均通过。这些测试没有覆盖全部新发现，不能据通过结果排除契约问题。

对 R-33、R-35，子 agent 从当前源码抽取参数构造/型号匹配逻辑，在 Node 中执行断言，分别复现新选择规格变空字符串、完整型号误匹配为较短型号。其余结论基于源码、调用链和 SQL/DDL；尚未进行真实数据库并发或真机端到端复现。

本轮 Docker 容器命令失败（Docker Desktop Linux daemon 管道不存在），因此未运行容器并发测试。DSH 在初始化 ~/.dsh/profiles/headless/cordis.yml 时出现 EPERM；CodeBuddy 即使限为 Read/Grep/Glob、禁会话持久化，仍因 ~/.codebuddy/traces 写入 EPERM 退出。两者均未生成模型审查结果，本轮没有使用它们的审查结论。

## 边界

未验证真实微信登录/支付/退款、生产部署运行状态、微信真机 UI、管理端浏览器端到端操作和第三方依赖的完整安全性。退款 afterCommit 的候选事务问题证据不足，未列为确定缺陷。

小程序明确提示支付未开放，管理端明确禁用后续处理/完成按钮，未将这些阶段报为当前可达入口缺陷；这意味着新订单自提完成及相应营业统计的业务闭环尚未开放。R-38 的范围为已有、历史或导入的完成订单。

## 修复记录（2026-10-14）

| ID | 已实施的修复 | 回归证据 |
| --- | --- | --- |
| R-30 | 登录数据库查询和 Redis 计数统一移除尾部 ASCII 空格，保留大小写及前导空格。 | EmployeeLoginIdentityTest：锁定、失败计数、成功清除、非等价输入。 |
| R-31 | V9 为 openid 加唯一约束；并发创建遇到重复键后读取已持久化身份，无对应身份则继续抛出原异常。 | UserLoginConcurrencyTest、UserCreationConcurrencyIT：真实 MySQL 同一 openid 首次并发登录只保留一条记录、返回同一 ID；微信 HTTP 使用测试 mock。 |
| R-32 | V10 为计划/商品加唯一约束，重复提交返回业务错误，待确认及已确认记录均不能再次创建差额。 | StockConcurrencyIT：串行、并发重复提交及确认后重提。 |
| R-33 | 规格弹窗使用本次选择，购物车增减使用具体行规格；商品列表数量汇总各规格，单行减号使用对应规格，多行打开购物车选择。 | 小程序 VM 执行真实页面方法，覆盖已有商品再选规格、行规格保留及旧规格减号边界。 |
| R-34 | 新增精确购物车行锁查询，默认规格只匹配 NULL/空字符串，列表查询保持独立。 | OrderSubmissionConcurrencyIT：默认规格增减、恢复不触碰其他规格，NULL/空字符串表示同一默认规格。 |
| R-35 | 先全列表精确匹配，再匹配有限设备附加文本；缺失 Pro/Max/Plus 型号时不选择基础型号。 | 小程序 VM 型号匹配回归。 |
| R-36 | 管理端列表、编辑回填和提交统一 positionCode/layerNum，移除无后端契约的行列输入。 | Vue 挂载表单测试校验列表、回填及提交 payload。 |
| R-37 | 再来一单成功后使用 switchTab 进入商城。 | VM 测试校验实际 tabBar 注册及调用顺序。 |
| R-38 | 销售额使用 SUM(od.amount * od.number)。 | 真实 MySQL 完成订单数量 3、单价 100，排行返回 300。 |

### 本轮验收结果

- Java 17 执行 `mvn -B -f sky-take-out/pom.xml verify`：215 个单元测试、52 个容器集成测试通过，零失败、零错误、零跳过，BUILD SUCCESS。集成测试使用真实 MySQL/Redis/RabbitMQ。
- 小程序三个 Node 测试文件：15 个测试通过；管理端 Jest：8 个套件、20 个测试通过；管理端生产构建成功，仍有已有包体积警告。
- 针对性 Java 回归先复现业务失败，修复后 36 个测试通过。全量首轮暴露新增登录并发测试上下文残留监听器，补充 AFTER_CLASS 上下文关闭后重跑全量通过。
- 无聊天上下文的只读子 agent 复审本轮差异，未发现其他确定缺陷；复审包含 Node VM 请求复现，未运行真实前端界面。

### 上线前数据检查

在生产数据库备份或受控快照上先执行以下只读检查；出现重复时需依据业务记录人工核实。迁移不会删除或自动合并历史用户、盘点记录，重复会阻止唯一约束上线；需处理失败迁移状态后再重试。

```sql
SELECT openid, COUNT(*) FROM user
WHERE openid IS NOT NULL GROUP BY openid HAVING COUNT(*) > 1;
SELECT plan_id, product_id, COUNT(*) FROM stock_check_record
GROUP BY plan_id, product_id HAVING COUNT(*) > 1;
```

FlywayMigrationIT 验证空库和旧结构升级到 V10、重复执行无操作、多个 NULL openid 可保留、历史重复身份及盘点记录阻止迁移且原数据保留。历史备忘录归属继续隔离，不推断 owner_type。

真实微信商户登录/支付/退款、生产数据快照迁移和微信真机端到端仍为上线前待验项。测试采用 Java 17；Java 21 与当前 Mockito/Byte Buddy 静态 mock 不兼容。
