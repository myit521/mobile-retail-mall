# 数码零售商城｜Java 后端工程化改造

这是一个面向数码零售场景的全栈商城项目，包含 Spring Boot 后端、Vue 管理端和 uni-app 微信小程序端。项目由教学型商城逐步改造为更接近企业研发流程的工程实践，当前重点是后端模块边界、数据库版本管理、自动化测试和持续集成。

> 本仓库对应 Java 后端项目，不包含 Agent 项目。README 只记录已经落地且可以从代码或测试中验证的能力；尚未完成的内容统一列在路线图中。

## 已落地的工程能力

- **模块化单体**：保持单应用部署，按 `auth`、`product`、`inventory`、`order`、`payment`、`notification` 六个业务域划分包边界；跨模块调用通过公开 API 完成。
- **架构约束自动化**：使用 ArchUnit 检查跨模块内部依赖、Controller 直连持久层和业务代码归属，防止模块边界随迭代退化。
- **数据库版本管理**：使用 Flyway 管理基线结构，兼容空数据库初始化和已有数据库接管，避免依赖手工执行散落 SQL。
- **分层测试体系**：JUnit 5、Mockito 与 Testcontainers 覆盖单元测试、Spring 上下文、MVC 契约和真实 MySQL/Redis/RabbitMQ 集成场景。
- **持续集成门禁**：GitHub Actions 执行 Maven `clean verify`，并生成 JaCoCo 覆盖率报告和可执行 Spring Boot Jar。
- **基础业务能力**：支持用户与员工认证、商品与分类管理、购物车、订单、库存、支付回调、订单事件通知等商城流程。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 2.7.3、Spring MVC、MyBatis |
| 数据与缓存 | MySQL、Flyway、Redis、Druid |
| 消息与通知 | RabbitMQ、WebSocket |
| 接口与安全 | JWT、Redis 会话、Bean Validation、Knife4j |
| 测试与质量 | JUnit 5、Mockito、Testcontainers、ArchUnit、JaCoCo |
| 前端 | Vue、TypeScript、uni-app |

## 后端架构

```text
客户端（Vue 管理端 / uni-app 小程序）
                 │ HTTP
                 ▼
┌───────────────────────────────────────────────┐
│              Spring Boot 单体应用             │
│                                               │
│ auth      product      inventory              │
│ order     payment      notification           │
│                                               │
│ 每个业务域：api（公开边界）+ internal（内部实现）│
└──────────────────────┬────────────────────────┘
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
        MySQL        Redis       RabbitMQ
```

后端仍然构建为一个 `sky-server` 可执行 Jar，没有为展示技术栈而拆分微服务。业务边界及允许的协作方式详见 [模块边界说明](sky-take-out/docs/architecture/module-boundaries.md)。

## 仓库结构

```text
.
├── sky-take-out/                         # Java 后端 Maven 多模块工程
│   ├── sky-common/                       # 通用组件
│   ├── sky-pojo/                         # DTO、VO 与实体
│   ├── sky-server/                       # 应用入口和领域模块
│   └── docs/                             # 架构与基线文档
├── project-sky-admin-vue-ts/             # Vue 管理端
└── project-rjwm-weixin-uniapp-develop-wsy/ # uni-app 微信小程序端
```

## 本地验证

### 环境要求

- JDK 17
- Maven 3.8+
- Docker Desktop（运行 Testcontainers 集成测试时需要）

### 构建与测试

在仓库根目录执行：

```bash
mvn -B -f sky-take-out/pom.xml clean verify
```

该命令会执行编译、单元测试、架构规则、MVC 契约测试和 Testcontainers 集成测试，并在 `sky-take-out/sky-server/target/` 生成可执行 Jar。

只构建后端、不运行测试：

```bash
mvn -B -f sky-take-out/pom.xml -DskipTests package
```

应用配置入口位于 `sky-take-out/sky-server/src/main/resources/application.yml`。开发环境通过 `application-dev.yml` 读取环境变量；本地私有配置可放入未提交的 `application-dev.local.yml`。数据库、Redis、微信支付、OSS、地图和模型服务凭据均不得提交到仓库，公开部署前应通过环境变量或密钥管理服务注入。

## 当前改造进度

已完成：

- 仓库与 HTTP API 基线梳理
- 测试基础设施与真实中间件集成测试
- Flyway 数据库基线迁移
- CI、ArchUnit 与 JaCoCo 质量门禁
- 六业务域模块化单体边界改造

后续路线图：

- 订单状态条件更新与并发冲突处理
- 库存扣减、释放的并发安全与幂等
- 支付回调事务一致性与 Transactional Outbox
- RabbitMQ 消费幂等、有限重试和死信处理
- 可观测性、容器化部署与可复现压测报告

路线图表示尚在实施或验证中的内容，不应视为当前版本已经具备的能力。

## 延伸文档

- [当前仓库基线](sky-take-out/docs/baseline/current-state.md)
- [HTTP API 契约基线](sky-take-out/docs/baseline/api-contract.md)
- [模块化单体边界](sky-take-out/docs/architecture/module-boundaries.md)
- [后端 API 文档](sky-take-out/API_DOCUMENTATION.md)
