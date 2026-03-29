# 数码零售商城系统

<div align="center">

# 数码零售商城系统

**mobile-retail-mall**

一个基于 **Spring Boot + Vue2 + uni-app** 的数码零售商城示例项目，
包含 **后端服务、管理端后台、小程序端前台** 三部分，适合作为商城类课程设计、毕业设计改造、练手项目和 GitHub 作品集展示。

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x-brightgreen)
![Vue](https://img.shields.io/badge/Vue-2.x-42b883)
![uni-app](https://img.shields.io/badge/uni--app-miniapp-orange)
![License](https://img.shields.io/badge/License-MIT-black)

</div>

---

## 快速导航

- [项目亮点](#项目亮点)
- [项目简介](#项目简介)
- [仓库结构](#仓库结构)
- [功能清单](#功能清单)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [适用说明](#适用说明)
- [部署指南](#部署指南)
- [小程序部署](#小程序部署)
- [联系方式](#联系方式)
- [License](#license)

---

## 项目亮点

- 三端结构完整：后端 / 管理端 / 小程序端分层清晰
- 商城业务骨架较完整：商品、分类、购物车、订单、后台管理等核心链路齐全
- 技术栈常见：适合学习、答辩、简历项目包装和二次开发
- 保留扩展位：Redis、RabbitMQ、OSS、微信能力接入位都还在

---

## 项目简介

本项目围绕“数码零售商城”场景整理，目标不是只提供一套页面，而是保留一个可继续开发、可本地运行、可二次改造的商城系统基础版本。

适合的使用场景：

- Java Web / Spring Boot 商城项目实战练习
- 商城类毕业设计、课程设计、答辩项目参考
- 将旧模板项目改造成零售商城项目的二开底板


---

## 仓库结构

```text
mobile-retail-mall/
├── backend/        # 后端服务（Spring Boot）
├── admin-web/      # 管理端（Vue2 + TypeScript）
├── miniapp/        # 小程序端（uni-app）
└── README.md
```

### 目录说明

| 目录 | 说明 |
|---|---|
| `backend` | 后端服务，负责商品、分类、购物车、订单、鉴权、缓存、消息等业务能力 |
| `admin-web` | 管理端后台，负责商品管理、分类管理、订单管理和基础运营页面 |
| `miniapp` | 用户端小程序，负责首页、商城、商品详情、购物车、下单、个人中心 |

### backend

后端服务目录，主要负责：

- 用户端接口
- 管理端接口
- 商品、分类、购物车、订单等核心业务
- Redis 缓存支持
- RabbitMQ 订单事件能力
- 微信登录 / 支付接入位
- OSS / 第三方服务接入位

### admin-web

商城管理后台目录，主要用于：

- 商品管理
- 分类管理
- 订单管理
- 商城后台基础运营页面
- 管理端业务操作与状态处理

### miniapp

用户端小程序目录，主要包含：

- 首页门户
- 商城列表
- 商品详情
- 购物车
- 下单与订单查询
- 个人中心

---

## 功能清单

### 用户端功能

- 商品分类浏览
- 商品搜索
- 商品详情查看
- 加入购物车
- 提交订单
- 订单列表 / 订单详情
- 用户中心基础能力

### 管理端功能

- 分类管理
- 商品管理
- 订单管理
- 订单状态流转处理
- 后台基础数据展示

### 后端能力

- JWT 登录鉴权
- Bean Validation 参数校验
- 全局异常处理
- Redis 缓存支持
- RabbitMQ 最小闭环事件流
- 日志规范化
- 微信支付相关接入位
- OSS 上传接入位

---

## 技术栈

| 模块 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 2.x、MyBatis、Maven |
| 数据层 | MySQL、Redis |
| 消息能力 | RabbitMQ |
| 管理端 | Vue 2、TypeScript、Element UI、Vue CLI |
| 小程序端 | uni-app、uni-ui（部分组件） |

---

## 适用说明

这个仓库适合作为：

- 学习型项目
- 改造型项目底板
- 演示型作品
- 校园答辩展示项目

### 适用规模

**当前架构（单实例后端）适合：**

| 指标 | 估算值 | 说明 |
|---|---|---|
| 日活用户（DAU） | 1,000 - 5,000 | 平均分布场景 |
| 并发请求 | 200 - 500 | Tomcat 默认线程池限制 |
| 日订单量 | 500 - 2,000 | 涉及 DB 事务 |
| WebSocket 连接 | ≤ 1,000 | 代码限制 `MAX_CONNECTIONS` |
| 页面 UV/天 | 1 万 - 5 万 | 假设平均分布 |

**典型场景：**
- 校园周边数码店
- 小型零售门店
- 课程设计/毕业设计演示
- 初创项目 MVP 验证

**超出规模需扩展：**
- 后端多实例 + 负载均衡（Nginx/SLB）
- 数据库读写分离/分库分表
- Redis 集群
- WebSocket 分布式改造（Redis Pub/Sub）
- 静态资源 CDN 加速

当前开源版本为可继续开发的基础版，使用前仍需补齐正式环境配置、第三方平台参数以及部署侧资源。
---

## 快速开始

### 1）准备基础环境

建议先准备：

- JDK 17
- Maven 3.8+
- MySQL 8.x
- Redis
- RabbitMQ
- Node.js 16+
- npm
- HBuilderX
- 微信开发者工具（如需调试微信小程序）

### 2）启动后端

进入 `backend`：

```bash
cd backend
```

重点配置文件：

- `backend/mall-server/src/main/resources/application.yml`
- `backend/mall-server/src/main/resources/application-dev.yml`
- `backend/mall-server/src/main/resources/application-prod.example.yml`
- `backend/mall-server/src/main/resources/db/mall.sql`

说明：

- `application.yml` 为主配置
- `application-dev.yml` 为脱敏后的示例开发配置
- `application-prod.example.yml` 为生产环境示例模板
- 数据库、Redis、RabbitMQ、OSS、微信等参数需按实际环境填写

常用命令：

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

仅验证编译是否通过：

```bash
mvn -q -DskipTests compile
```

### 3）启动管理端

进入 `admin-web`：

```bash
cd admin-web
npm install --legacy-peer-deps
npm run serve
```

**环境配置说明：**

复制示例配置并按实际环境修改：

```bash
cp .env.example .env.development    # 开发环境
cp .env.example .env.production     # 生产环境
```

`.env.production` 参考配置：

```bash
NODE_ENV = 'production'
VUE_APP_NODE_ENV = 'prod'
VUE_APP_BASE_API = '/api/admin'
VUE_APP_URL = 'https://your-domain.com'
VUE_APP_SOCKET_URL = 'wss://your-domain.com/ws/'
VUE_APP_DELETE_PERMISSIONS = false
```

**关键配置说明：**

| 变量 | 说明 |
|---|---|
| `VUE_APP_BASE_API` | 后端接口代理路径，Nginx 反代为 `/api/admin` → `127.0.0.1:8080` |
| `VUE_APP_SOCKET_URL` | WebSocket 地址，用于订单实时报警 |
| `VUE_APP_URL` | 前端访问域名 |

**路径匹配说明：**

后端 Controller 统一使用 `@RequestMapping("/admin/xxx")` 前缀，因此：
- 前端请求：`VUE_APP_BASE_API` + API 路径 = `/api/admin/employee/login`
- Nginx 反代：`/api/` → `http://127.0.0.1:8080/`
- 后端接收：`/admin/employee/login`（与 `@RequestMapping("/admin/employee")` 匹配）

如果登录返回 404，请检查：
1. `VUE_APP_BASE_API` 是否配置为 `/api/admin`（不是 `/api`）
2. Nginx `location /api/` 是否正确反代到后端 8080 端口

**常用构建命令：**

```bash
# 开发环境运行
npm run serve

# 生产环境构建
npm run build

# 依赖安装失败时（旧项目兼容）
npm install --legacy-peer-deps
```

**构建输出：**

构建成功后生成 `dist/` 目录，部署到 Nginx 静态目录（如 `/var/www/mall`）。

**常见问题：**

1. **依赖安装失败**：使用 `--legacy-peer-deps` 参数（TypeScript 3.6 兼容）
2. **接口 404**：检查 Nginx 反代配置，确保 `/api/` → 后端 8080 端口
3. **WebSocket 连不上**：检查 Nginx `/ws/` 反代 + 安全组 443 放行

### 4）启动小程序端

进入 `miniapp`：

```bash
cd miniapp
npm install
```

**配置说明：**

| 文件 | 配置项 | 说明 |
|---|---|---|
| `manifest.json` | `mp-weixin.appid` | 微信小程序 AppID |
| `project.config.json` | `appid` | 微信开发者工具项目 ID |
| `utils/env.js` | `baseUrl` | 后端接口地址 |

**开发环境配置：**

编辑 `utils/env.js`：

```javascript
// 本地调试
export const baseUrl = 'http://localhost:8080'

// 真机调试（改成你电脑局域网 IP）
// export const baseUrl = 'http://192.168.x.x:8080'

// 生产环境（按实际域名修改）
export const baseUrl = 'https://your-domain.com'
```

**调试方式：**

1. **HBuilderX**：运行 → 运行到小程序模拟器 → 微信开发者工具
2. **微信开发者工具**：导入 `dist/dev/mp-weixin` 目录

---

### 5）小程序部署上线

**步骤 1：微信公众平台配置**

1. 登录 https://mp.weixin.qq.com
2. 开发 → 开发管理 → 开发设置
3. **服务器域名** 添加：
   - `request` 合法域名：`https://your-domain.com`
   - `socket` 合法域名（如需 WebSocket）

**步骤 2：修改生产配置**

编辑 `miniapp/utils/env.js`：

```javascript
// 生产环境
export const baseUrl = 'https://your-domain.com'
```

编辑 `miniapp/manifest.json`（确认 AppID）：

```json
"mp-weixin": {
  "appid": "your-wx",
  ...
}
```

**步骤 3：编译上传**

使用 HBuilderX：
1. 发行 → 发行微信小程序
2. 设置 AppID（如未自动读取）
3. 上传代码（填写版本号和备注）

或使用微信开发者工具：
1. 导入 `dist/build/mp-weixin` 目录
2. 点击右上角「上传」

**步骤 4：提交审核**

1. 登录微信公众平台
2. 版本管理 → 选择刚上传的版本
3. 提交审核 → 填写审核信息

---

## 配置说明

### 需补充的配置内容

使用本项目时，需按实际环境替换：

- MySQL 连接信息
- Redis 连接信息
- RabbitMQ 连接信息
- 微信小程序 AppID
- 微信支付商户号、证书、APIv3 Key
- OSS 存储桶与访问密钥
- 实际请求域名、上传域名、回调地址
- 生产环境部署参数

---

## 部署指南

### 后端部署

**1. 编译打包：**

```bash
cd backend/mall-server
# 先安装公共模块
mvn -pl sky-common,sky-pojo -am install -DskipTests
# 再启动服务
mvn spring-boot:run
```

**2. 后台运行：**

```bash
nohup mvn spring-boot:run > sky-server.log 2>&1 &
```

**3. 验证启动：**

```bash
netstat -tlnp | grep 8080
# 或
tail -f sky-server.log
```

---

### JVM 配置建议

**开发环境（本机调试）：**
```bash
# 默认配置即可，Maven 会自动使用合理参数
mvn spring-boot:run
```

**生产环境（服务器部署）：**

根据服务器内存推荐配置：

| 服务器内存 | JVM 参数建议 | 说明 |
|---|---|---|
| 1GB | `-Xms512m -Xmx512m` | 最低配置，仅演示/测试 |
| 2GB | `-Xms1g -Xmx1g` | 小型项目推荐 |
| 4GB | `-Xms2g -Xmx2g` | 中型项目推荐 |
| 8GB+ | `-Xms4g -Xmx4g` | 高并发场景 |

**生产启动脚本示例（2GB 内存服务器）：**

```bash
#!/bin/bash
# start.sh

APP_NAME="mall-server"
JAR_FILE="target/mall-server-1.0.0.jar"
LOG_FILE="logs/sky-server.log"

# JVM 参数
JAVA_OPTS="-server"
JAVA_OPTS="$JAVA_OPTS -Xms1g -Xmx1g"              # 堆内存
JAVA_OPTS="$JAVA_OPTS -XX:MetaspaceSize=256m"     # 元空间
JAVA_OPTS="$JAVA_OPTS -XX:MaxMetaspaceSize=256m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"               # G1 垃圾回收器
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=200"   # 最大 GC 停顿时间
JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError"  # OOM 时 dump
JAVA_OPTS="$JAVA_OPTS -XX:HeapDumpPath=logs/heap_dump.hprof"
JAVA_OPTS="$JAVA_OPTS -Djava.security.egd=file:/dev/./urandom"  # 随机数源

# 创建日志目录
mkdir -p logs

# 后台启动
nohup java $JAVA_OPTS -jar $JAR_FILE > $LOG_FILE 2>&1 &

echo "$APP_NAME 已启动，PID: $!"
echo "日志：tail -f $LOG_FILE"
```

**监控 GC 状态：**
```bash
# 查看 GC 统计
jstat -gc <pid> 1000

# 查看堆内存使用
jmap -heap <pid>

# 实时内存监控
jconsole <pid>
```

**优化建议：**
1. 堆内存设置为物理内存的 50%-70%（预留系统和其他服务）
2. `-Xms` 和 `-Xmx` 设为相同值，避免内存抖动
3. 开启 G1 垃圾回收器（JDK 9+ 默认）
4. 生产环境建议添加 `-Duser.timezone=Asia/Shanghai`

### 管理端部署

**1. 修改环境配置：**

编辑 `.env.production`：

```bash
VUE_APP_BASE_API = '/api/admin'
VUE_APP_SOCKET_URL = 'wss://your-domain.com/ws/'
```

**2. 构建：**

```bash
npm run build
```

**3. 部署到 Nginx：**

```bash
rsync -av --delete dist/ /var/www/mall/
```

### Nginx 配置要点

**1. 静态文件 + 接口反代：**

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;

    # 静态文件
    location / {
        root /var/www/mall;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 管理端接口反代
    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 小程序端接口反代
    location /user/ {
        proxy_pass http://127.0.0.1:8080/user/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket 反代
    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

**2. 路径匹配说明：**

| 端 | 前端请求路径 | Nginx 反代 | 后端接收路径 | Controller 映射 |
|---|---|---|---|---|
| 管理端 | `/api/admin/xxx` | `/api/` → `8080/` | `/admin/xxx` | `@RequestMapping("/admin/xxx")` |
| 小程序端 | `/user/xxx` | `/user/` → `8080/user/` | `/user/xxx` | `@RequestMapping("/user/xxx")` |

**2. 关键点：**

- `try_files $uri $uri/ /index.html;` 解决 Vue Router history 模式刷新 404
- WebSocket 需要 `Upgrade` 和 `Connection` 头
- 确保云安全组放行 443 端口

### 常见问题排查

| 问题 | 可能原因 | 解决方案 |
|---|---|---|
| 前端构建失败 | 依赖版本冲突 | `npm install --legacy-peer-deps` |
| 接口 404 | Nginx 未反代 | 检查 `location /api/` 配置 |
| WebSocket 连不上 | 安全组未放行 443 | 云服务器控制台放行 |
| Redis 连接失败 | 未启动 Redis | `systemctl start redis-server` |
| 后端启动失败 | 端口占用/配置错误 | 查看 `sky-server.log` |


---

## 联系方式

- 邮箱：`chihior_ning@qq.com`,`2621455118@qq.com`
---

## License

本项目以仓库根目录 `LICENSE` 为准，建议使用 MIT。
