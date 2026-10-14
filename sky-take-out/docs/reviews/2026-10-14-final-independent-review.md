# 最后一轮独立审查（2026-10-14）

## 审查方式与版本

本地审查提交：`cc6fcff863e15d63fde5131e4aa841c2fb1c0c56`。对应已发布的业务代码为 GitHub 提交 `fac9ef288c5fcc9d033fa670b09253e531af1bf8`；二者仅保留远端既有 CI 和 gitleaks 配置差异。

主审查子 agent 使用 `fork_turns=none`，没有继承聊天上下文，没有读取 memory、旧审查报告或 OpenSpec。它另分派鉴权和前端契约两名只读子审查者，逐项核实结论。协调主进程随后重读关键代码和测试报告。审查覆盖鉴权、商品/购物车、库存、订单、支付退款、消息/Outbox、迁移、WebSocket、备忘录和前端主要契约。

审查前后 HEAD 和工作区变更集合一致；未提交首页样式、OpenSpec 和历史手工测试不属于审查范围。独立审查结束时只记录发现；随后用户授权修复，实施与验收见文末。

## 确认的新问题

共 **4 项 P2**，未确认新的 P0/P1。这不是全项目无缺陷保证。

### R-39：补货后商品列表缓存仍显示零库存

- 主位置：`sky-server/src/main/java/com/sky/inventory/internal/application/StockServiceImpl.java:184`。
- 关联：`ProductServiceImpl.java:194,229` 缓存含库存的商品列表；`CacheConfig.java:60` 配置 30 分钟 TTL；小程序 `pages/index/index.js:385` 按库存不大于零拒绝加车。
- 场景：零库存商品被分类或型号列表缓存，管理员手动补货后库存更新路径没有驱逐商品列表缓存；用户刷新仍拿到零库存，不能加车，直到缓存过期或其他商品编辑驱逐缓存。
- 本轮复现使用真实 ProductServiceImpl、StockServiceImpl、Spring CacheInterceptor，加 Mock Mapper 和 ConcurrentMapCacheManager，输出：

```text
BEFORE=0
REAL_STOCK=5, REFRESHED_CATALOG_STOCK=0
```

这是业务方法与缓存拦截器的内存复现，未做同场景的真实 Redis 实测。建议库存成功提交后失效对应商品列表缓存，并覆盖补货、盘点、扣减与返还路径。

### R-40：OSS 上传失败仍返回成功 URL

- 主位置：`sky-common/src/main/java/com/sky/utils/AliOssUtil.java:37,44`。
- 关联：同文件第 66 行；`sky-server/src/main/java/com/sky/controller/admin/UploadController.java:47,49`；管理端 `src/components/ImgUpload/index.vue:72`。
- 场景：putObject 抛出 OSSException 或 ClientException，catch 仅输出错误，函数仍拼接并返回 URL；控制器包装成成功，前端可以将不存在的图片保存到商品。
- 证据：完整静态控制流，子审查者、主审查者和协调主进程均核对；未连接真实 OSS。
- 建议只有上传成功才返回 URL，失败传播为接口错误，同时保证客户端关闭。

### R-41：两个“再来一单”入口没有可靠进入商城

- 主位置：小程序 `pages/my/my.vue:183`；同类位置 `pages/historyOrder/historyOrder.vue:227`。
- 场景：我的页面是 tabBar 根页面，恢复购物车后 navigateBack 没有可回退页；从我的进入历史订单再操作，则返回我的而不是商城。详情页面的相同功能已经使用 switchTab，其他两个入口仍旧。
- 子审查者与主审查者分别在只读 Node VM 执行原页面方法，提供真实可出现的页面栈，记录导航调用：

```text
MY_NAV [{"delta":1}] availableBackPages=0
HISTORY_NAV [{"delta":1}] destination=pages/my/my
```

未做微信开发者工具或真机验证。建议两个入口统一 switchTab 到 `/pages/index/index`，分别补入口回归。

### R-42：历史订单筛选被迟到响应污染

- 主位置：小程序 `pages/historyOrder/historyOrder.vue:211,212`，关联第 234 行 changeTab。
- 场景：全部订单请求未返回时切到已取消；取消请求先返回，全部请求后返回。响应没有核实其所属筛选，直接追加记录并覆盖 total。
- 双方分别在 Node VM 执行实际 getList/changeTab，使用可控制 Promise 重现响应顺序，结果：

```json
{"tab":2,"status":6,"records":[{"id":6,"status":6,"orderDetailList":[]},{"id":2,"status":2,"orderDetailList":[]}],"total":7}
```

已取消列表出现待确认订单，分页总数也错误。建议给筛选/分页请求关联有效版本，过期响应不修改当前列表及分页状态。

## 已复现但需确认规则的行为

盘点计划完成后仍可提交、确认记录。StockServiceImpl 第 224 行只检查计划存在；第 264 行确认记录不检查计划状态；第 311 行完成计划不检查是否存在待确认记录。已登录员工可调用相应 StockController API，但当前管理端没有盘点页面。

主审查者用真实服务和 Mock Mapper 向状态 2 的计划提交记录，输出 `COMPLETED_PLAN_ACCEPTED_NEW_RECORD=true`。如果“完成”表示封存，应禁止后续提交/确认；若允许补录，需要明确语义。本次不依据隐含业务规则将它计入 4 项确定缺陷。

## 本轮新运行的验证

Java 使用 `C:\Users\17148\.jdks\ms-17.0.19`。下列结果属于本轮独立审查新验证，不是此前 267 个全量测试结果的复述。

- 六类单元测试：StockServiceImplTest、MessageConsumptionServiceTest、PaymentCallbackServiceTest、RefundServiceTest、OrderServiceTransitionTest、LatePaymentRaceTest，共 **46 个，零失败、零错误、零跳过**。
- 三类 Docker 集成测试：OrderConditionalUpdateIT（3）、PaymentCallbackServiceIT（5）、OrderEventConsumerIT（3），共 **11 个，零失败、零错误、零跳过**。
- Testcontainers 使用 MySQL、Redis、RabbitMQ；10 个 Flyway 迁移在测试库成功应用。微信与通知服务为测试 Mock。
- 初次单元测试因沙箱 Java attach 管道权限挂起，停止后正常申请权限重跑成功；Docker 操作也经过正常权限申请。

```powershell
$env:JAVA_HOME='C:\Users\17148\.jdks\ms-17.0.19'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -B -f sky-take-out/pom.xml -pl sky-server -am '-Dtest=StockServiceImplTest,MessageConsumptionServiceTest,PaymentCallbackServiceTest,RefundServiceTest,OrderServiceTransitionTest,LatePaymentRaceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -B -f sky-take-out/pom.xml -pl sky-server -am '-Dtest=OrderConditionalUpdateIT,PaymentCallbackServiceIT,OrderEventConsumerIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

测试报告位于 `sky-server/target/surefire-reports/`。前端方法复现通过 PowerShell here-string 管道送入 Node：读取原 Vue script、去掉 import、放入 VM；使用 Mock API Promise 和页面栈，不写入或修改源码。库存/盘点复现通过 JShell 临时内存执行，无真实生产调用。

## 排除项与验收边界

- 购物车 mapper 返回现价，排除“旧价格刷新无法恢复结算”的疑点。
- 未完成筛选 status=1 在后端映射为 `(1,2,3,4)`，没有该契约错误。
- 本次未确认身份互换、订单 owner 越权、Memo owner_type 越权或 WebSocket ticket 复用。
- 支付入口明确提示未开放，不能视为当前小程序已经具备实际支付闭环。
- FAILED 消费记录没有现成重放恢复流程，但缺乏明确恢复入口契约，列为运维边界而非确定代码缺陷。
- 新库迁移通过不能代表生产历史数据升级通过；V9/V10 的历史重复数据检查仍是上线前必验项，不能自动删除或合并。
- 未连接真实微信、OSS 或生产服务；真机界面、真实支付退款、平台证书轮换、生产历史数据迁移仍待环境验收。历史备忘录继续保留隔离状态。

## 用户授权后的修复（2026-10-14）

- R-39：四种实际库存变更在外层事务提交后失效 product 缓存；回滚及无变化操作不清理。使用立即失效 API，避免事务感知缓存在 afterCommit 内再次延后执行。缓存清理异常仅记录错误类型，避免已提交业务误报失败；清理失败时仍有缓存 TTL 内的数据陈旧风险，需要按告警处理。
- R-40：OSSException/ClientException 转为统一上传失败业务错误，保留 finally 关闭客户端；只有 putObject 成功才返回 URL。HTTP 回归使用 Mock OSS，拒绝和断连均返回失败，成功返回 URL。
- R-41：我的及历史订单恢复购物车后统一 switchTab 到已注册商城 tab。
- R-42：筛选及刷新生成新请求代号，旧代响应不更新列表/总数或解除当前加载锁；分页入口串行加载，同代合法分页保留。
- 盘点完成后的补录语义没有更改，继续列为规则待确认项。

针对性红绿：上传两个错误场景原先返回 HTTP 200，修复后不返回成功；四种库存变更原先提交后仍保留缓存，已新增事务提交/回滚与缓存失败测试；前端新增六个真实页面方法回归先失败，修复后小程序共 21 个测试通过。另补真实 MySQL/Redis 集成用例验证提交后分类/型号缓存消失、回滚后缓存和库存保留。

最终验收：Java 17 执行 `mvn -B -f sky-take-out/pom.xml verify`，231 个单元测试、54 个容器集成测试通过，零失败、零错误、零跳过，BUILD SUCCESS。首轮因 Docker 未启动阻断，恢复 Docker 后完整重跑成功。小程序 Node 21 个测试、管理端 Jest 8 套件 20 个测试通过。本轮只读子 agent 复审发现缓存清理失败冒泡，补充故障回归先红后修复，全量已包含该测试；复审者再次核对确认该问题已解决。

四项确定问题已修复；真实 OSS/微信、真机、生产历史数据迁移依然未验证。缓存故障期间的数据陈旧和盘点补录规则不因本轮测试通过而消除。
