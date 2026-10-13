# 修复后重新审查（2026-10-13）

审查版本：本机 `d9563de`；GitHub `b0bcdaa`。业务源码、迁移和测试内容一致，GitHub 版本保留已有的 CI 与 Gitleaks 配置。审查覆盖订单支付退款、库存、购物车、鉴权、备忘录和两个前端；下表记录发现时的问题，当前修复与验证结果见文末。

## 新发现

| ID | 优先级 | 问题 | 触发条件和影响 | 证据 |
| --- | --- | --- | --- | --- |
| R-24 | 高 / P1 | 商品编辑用旧表单库存覆盖订单扣减 | 管理员打开编辑页时库存为 10，期间订单扣至 9；管理员仅改名称后保存仍提交 stock=10，绝对赋值恢复已售库存，可能导致超卖。R-03 库存服务修复未覆盖商品编辑入口。 | `sky-server/src/main/resources/mapper/ProductMapper.xml:74-76`；`../project-sky-admin-vue-ts/src/views/product/addProduct.vue:234-238,290` |
| R-25 | 中 / P2 | 再来一单可能成功返回却未恢复购物车 | MySQL REPEATABLE READ 下，repetition 先读历史订单建立快照，再等待用户锁。并发 submit 清车提交后，restore 的普通查询仍看到被删除的旧行，按旧 ID 更新 0 行后直接继续，购物车最终为空。 | `sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:308-311,328-333`；`sky-server/src/main/java/com/sky/product/internal/application/ShoppingCartServiceImpl.java:122-135` |
| R-26 | 中 / P2 | 调价后刷新购物车仍返回旧金额，提交持续被拒绝 | 10 元加购后调价至 12 元，list 继续返回持久化 amount=10，已有行加购也只改数量。submit 按现价要求 12 元，提示刷新却无法通过刷新恢复。历史订单恢复也沿用旧价。该问题是购物车和提交 API 的契约矛盾；当前小程序订单页禁用支付，不能据此声称该页面已可直接点支付复现。 | `sky-server/src/main/java/com/sky/order/internal/application/OrderServiceImpl.java:108-111`；`sky-server/src/main/java/com/sky/product/internal/application/ShoppingCartServiceImpl.java:77-80,140`；`sky-server/src/main/resources/mapper/ShoppingCartMapper.xml:48-49`；`../project-rjwm-weixin-uniapp-develop-wsy/pages/index/index.js:558` |
| R-27 | 中 / P2 | 并发扣减和归还日志的库存前后值不真实 | 两个事务都先读到库存 10，再各原子扣 1，最终库存为 8，但两条日志都记 10→9。数量没有丢失，库存审计和排错证据失真。普通查询也可能早于并发手工调整。 | `sky-server/src/main/java/com/sky/inventory/internal/application/StockServiceImpl.java:60-68,79-80`；归还路径 `selectProductById` 和日志使用相同读值模式 |
| R-28 | 中 / P2 | 历史非标准角色登录成功后全部请求 401 | 登录把数据库角色 admin 或带空白的 ADMIN 仅在内存规范化；JWT/Redis 存 ADMIN，新增会话校验却严格比较原始数据库角色，导致正常历史员工立即失效，WebSocket 票据亦受影响。 | `sky-server/src/main/java/com/sky/auth/internal/application/EmployeeServiceImpl.java:80-81`；`sky-server/src/main/java/com/sky/auth/internal/application/TokenSessionServiceImpl.java:142-144` |
| R-29 | 中 / P2 | 迟到的旧请求 401 清除刚恢复的新 token | A、B 使用同一过期 token 并发请求；A 的 401 触发重登并写入新 token 后，B 的旧 401 无条件清除当前 token，使已成功恢复的登录再次失效。loginPromise 只合并登录调用，不能保护新会话。 | `../project-rjwm-weixin-uniapp-develop-wsy/utils/request.js:24-27`；`../project-rjwm-weixin-uniapp-develop-wsy/App.vue:50-51` |

## 修复建议

1. R-24：普通商品编辑不写库存，库存变动统一走专用调整入口；如果编辑必须设置库存，需要明确的版本或条件更新，并记录实际变化。
2. R-25：历史订单恢复在建立快照前就锁用户，或对锁后购物车使用当前读；检查更新行数，避免静默丢失恢复操作。补 submit/repetition 并发测试。
3. R-26：购物车读取/恢复同步当前有效商品价格，并使前端展示、服务端金额校验使用相同价格来源。
4. R-27：在同一事务中读取并锁定真实库存前值，再变更和写日志；用并发测试核对库存日志变化链。
5. R-28：登录与会话验证采用一致的角色规范化规则，或在可审计迁移中规范历史角色。
6. R-29：401 失效操作先核对请求发出时的 token 与当前 token，只清除对应会话，并合并恢复提示。补旧 401 晚于新登录成功的时序测试。

## 审查边界

- 此前完整 Maven verify 的 200 个常规测试、36 个集成测试，管理端 16 个测试和小程序 2 个测试通过，仍不能覆盖上表所有交错和入口。此次没有把既有通过结果当作新问题不存在的证据。
- 未发现新的确定 HTTP/WebSocket 跨身份越权或备忘录 owner 隔离遗漏。历史 owner_type=NULL 继续隔离，符合用户决定。
- 微信真实商户联调和部署数据快照演练继续作为上线前待验项；用户明确没有专用微信联调环境。
- 规格价格/库存字段未参与下单的既有路径，需要先明确 SKU 功能是否启用，暂不列为已确认缺陷。
- 首页样式、OpenSpec 和手工测试示例等未提交内容不在本轮审查范围。

## GitHub 推送说明

用户明确指定接收仓库 `myit521/mobile-retail-mall`。直接推送原分支被 GitHub Push Protection 拒绝，因为旧提交历史中的开发配置含阿里云凭据；没有使用解除保护功能，也未记录凭据内容。

在已有的干净 GitHub 历史 `aee3ae9` 上应用三项修复提交后推送成功：

- `178d908`：对应本机 `464fd83` 后端修复。
- `8e35b63`：对应本机 `5d46641` 前端修复。
- `b0bcdaa`：对应本机 `d9563de` 审查验收记录。

远端分支：`codex/enterprise-project-hardening`。旧历史命中的凭据是否已撤销无法在此次审查确认；其撤销或轮换需要凭据管理者核实。

## 六项修复与验收（2026-10-13）

| ID | 修复 | 回归证据 |
| --- | --- | --- |
| R-24 | 商品编辑从请求、服务对象和更新 SQL 中排除 stock，编辑页库存只读并引导至库存调整；新增商品仍保留初始库存。 | 旧表单 stock=10 保存后，已扣减库存保持为 9；商品编辑/新增前端契约测试通过。 |
| R-25 | restore 在用户锁后使用 FOR UPDATE 当前读，即使外层事务先前建立了旧快照，也读取已提交购物车状态。 | 固定时序测试先建立旧快照、等待下单清车提交，再执行 repetition；历史商品数量完整恢复。 |
| R-26 | 购物车列表联查商品现价，已加购及历史恢复的商品刷新后显示当前价格；提交仍以商品现价校验。 | 商品从 2.50 调至 3.00 后刷新并提交成功；历史恢复商品列表显示 3.00。 |
| R-27 | 扣减与归还先锁定当前商品库存，再执行原子变动及记录日志。 | 固定竞争时序验证日志链从 10→15 接续至 15→12 或 15→18，与最终库存相符。 |
| R-28 | 登录与数据库会话角色校验复用同一规范化规则，Redis 身份/角色与请求仍严格匹配。 | 六组历史角色可正常使用会话；身份不符、禁用、未知声明、角色变更仍被拒绝。 |
| R-29 | 401 只失效请求对应的当前 token，并合并并发恢复提示；取消、弹窗失败、登录结束均释放恢复状态。 | 旧请求 401 晚于新登录完成时不清除新 token；并发提示合并及取消后重新提示通过。 |

先执行新增回归测试确认旧实现失败：历史角色六组与购物车三项失败，商品编辑和库存审计四项失败；小程序迟到 401 契约也先复现失败。实现修复后，34 个定向 Java 测试全部通过。

最终 `mvn -B -f sky-take-out/pom.xml verify`：BUILD SUCCESS，209 个常规测试、42 个集成测试，均为 0 失败、0 错误、0 跳过。管理端完整 Jest 7 组 18 个测试通过，小程序两个 Node 契约测试通过，管理端生产构建通过（仍有既有资源体积和工具兼容警告）。独立只读复核未发现严重或重要的确定缺陷。

微信真实商户联调、部署数据库快照迁移与历史备忘录归属隔离，继续按此前确定的验收边界处理。
