# 上线实施行动指南

> 配套阅读：
> - [ONLINE_READINESS_GUIDE.md](./ONLINE_READINESS_GUIDE.md)
> - [TECHNICAL_REFERENCE.md](./TECHNICAL_REFERENCE.md)
>
> 目标：
> - 把当前项目部署到阿里云服务器
> - 让你知道每一步为什么要做
> - 让系统逐步具备 `100+` 并发的基础能力

---

## 1. 先建立正确预期

你现在的目标，不是一步把系统做成“大厂级高可用架构”，而是：

1. 先把项目正确、稳定地跑在生产环境
2. 再把最容易拖垮系统的瓶颈一个一个补掉
3. 最后通过压测，确认它是否真的能扛住 `100+` 并发

对于你现在这个项目，最现实、最适合学习的路线是：

- 第一阶段：单机上线
  - `1 台 2C4G ECS`
  - `1 个 Spring Boot 服务`
  - `1 个 MySQL`
  - `1 个 Redis`
  - `1 个 Nginx`
- 第二阶段：做性能优化
  - 索引
  - 缓存
  - 慢 SQL
  - JVM 调优
  - 压测
- 第三阶段：真的扛不住了，再考虑拆分

这条路线最适合你现在“边学边做”。

---

## 2. 先认识你现在项目的基础情况

先明确你项目已经具备了什么。

### 2.1 你现在已经有的基础

后端基础：

- JDK 版本：`17`
- Spring Boot 版本：`2.7.3`
- 项目类型：单体应用，最终以 `jar` 方式启动
- 配置入口：`[application.yml](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/resources/application.yml)`
- 当前默认 profile：`dev`

缓存基础：

- 你已经接上了 Spring Cache + Redis
- 位置：
  - `[CacheConfig.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/config/CacheConfig.java)`
  - `[ProductServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/ProductServiceImpl.java)`
  - `[PhoneModelServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/PhoneModelServiceImpl.java)`
  - `[ProductLocationServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/ProductLocationServiceImpl.java)`
- 说明：
  - 商品列表缓存已经有
  - 机型缓存已经有
  - 商品位置缓存已经有
  - 店铺状态也走了 Redis

已经做过的业务优化：

- 商品搜索已打通
- 购物车当前按用户隔离
- 历史订单查询已经用了批量查详情，而不是明显的 N+1

### 2.2 你现在还缺的关键项

这几项是后面上线前必须补的：

- 生产配置拆分
- Nginx 反向代理
- HTTPS
- 小程序正式域名配置
- 数据库索引核查
- 搜索与统计接口进一步优化
- 慢查询日志
- 压测
- 监控

---

## 3. 总体实施顺序

建议你严格按这个顺序做，不要跳。

### 第 1 步：把生产配置单独拆出来

目标：

- 保证生产环境不再依赖本地开发配置

你要学习的知识点：

- 什么是 `dev / prod` 环境隔离
- 为什么不能让生产环境默认跑 `dev`

### 第 2 步：准备服务器基础环境

目标：

- 让 ECS 具备运行 Java、Nginx、MySQL、Redis 的能力

你要学习的知识点：

- 什么是 ECS
- 什么是安全组
- 什么是反向代理

### 第 3 步：部署数据库和 Redis

目标：

- 先让后端具备稳定的数据存储和缓存环境

你要学习的知识点：

- MySQL 负责持久化
- Redis 负责减轻数据库压力

### 第 4 步：部署 Spring Boot 后端

目标：

- 让接口先跑起来

你要学习的知识点：

- `jar` 包部署
- `systemd` 托管服务

### 第 5 步：部署管理端前端

目标：

- 让后台页面可以通过域名访问

你要学习的知识点：

- Vue 项目打包
- Nginx 托管静态资源

### 第 6 步：处理小程序上线条件

目标：

- 让小程序从本地开发地址切到正式 HTTPS 域名

你要学习的知识点：

- request 合法域名
- HTTPS 域名
- 小程序后台配置

### 第 7 步：做并发优化

目标：

- 把系统从“能跑”变成“扛得住”

你要学习的知识点：

- 索引
- 缓存
- 慢 SQL
- 线程池
- JVM

### 第 8 步：压测和复盘

目标：

- 用数据证明系统是否真的能扛住 `100+` 并发

你要学习的知识点：

- 什么是并发
- 什么是响应时间
- 什么是 P95
- 为什么压测前后都要看日志和资源

---

## 4. 第一步：拆分生产配置

这是上线前第一件必须做的事。

### 4.1 你现在的现状

当前主配置在：

- `[application.yml](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/resources/application.yml)`

现在它里面有：

- `spring.profiles.active: dev`
- 本地开发导入：`application-dev.local.yml`

这意味着：

- 你现在默认启动就是开发环境
- 如果你把它原样拿去服务器，很容易把生产环境也跑成开发态

### 4.2 你该怎么做

#### 步骤 1：新增生产配置文件

在这个目录下新建：

- `sky-server/src/main/resources/application-prod.yml`

第一版内容建议先放：

- 生产数据库
- 生产 Redis
- 生产日志级别
- 生产 Swagger 开关
- 生产 JWT 密钥读取方式

注意：

- 不要把生产密码直接写死到仓库
- 可以先放占位符，服务器上再注入

#### 步骤 2：不要再让主配置默认写死 `dev`

你有两个可选做法：

- 做法 A：把 `application.yml` 里的 `spring.profiles.active` 去掉
- 做法 B：保留，但服务器启动时强制指定 `--spring.profiles.active=prod`

推荐你学做法 A，因为更规范。

#### 步骤 3：给生产环境加日志文件输出

在 `application-prod.yml` 里增加：

- 日志文件路径
- 日志级别

建议思路：

- `mapper` 不再用 `debug`
- `service/controller` 保持 `info`
- 错误日志单独看文件

### 4.3 做完后怎么验证

你需要确认三件事：

1. 本地 `dev` 还能继续跑
2. 生产启动时使用 `prod`
3. 生产日志没有打印一堆开发 SQL 调试内容

### 4.4 这一小步你学到什么

你要记住一句话：

> 生产环境最大的风险之一，不是代码本身，而是“拿开发配置直接上生产”。

---

## 5. 第二步：准备服务器

这里默认你用阿里云 ECS。

我参考了阿里云官方 ECS 文档和安全组文档来整理这一步，核心原则是：

- 安全组就是云上的防火墙
- 生产环境要遵循最小开放原则

参考：

- [Alibaba Cloud ECS Quick Start](https://www.alibabacloud.com/help/en/ecs/quick-start/)
- [Alibaba Cloud Security Groups](https://www.alibabacloud.com/help/en/ecs/user-guide/start-using-security-groups)
- [Alibaba Cloud Java Environment Deployment](https://www.alibabacloud.com/help/en/ecs/user-guide/manually-deploy-a-java-web-environment-using-tomcat)

### 5.1 先整理你要准备的东西

你需要提前准备：

- 一台 `2C4G` ECS
- 公网 IP
- 一个域名
- HTTPS 证书
- 服务器登录密码或密钥

### 5.2 安全组怎么开

推荐你这样开：

- `22`：只允许你自己的公网 IP 访问
- `80`：开放给公网
- `443`：开放给公网
- `8080`：不要直接开放给公网，尽量只给本机或内网
- `3306`：不要开放给公网
- `6379`：不要开放给公网

你要理解：

- `80/443` 给 Nginx
- `8080` 给 Spring Boot
- `3306` 给 MySQL
- `6379` 给 Redis

生产环境应该尽量只有 `80/443` 面向外网。

### 5.3 服务器目录怎么规划

登录 ECS 后，建议先建目录：

```bash
sudo mkdir -p /opt/mall/app
sudo mkdir -p /opt/mall/config
sudo mkdir -p /opt/mall/logs
sudo mkdir -p /opt/mall/backup
```

建议用途：

- `/opt/mall/app`：放 jar 包
- `/opt/mall/config`：放生产配置
- `/opt/mall/logs`：放应用日志
- `/opt/mall/backup`：放备份文件

### 5.4 安装运行环境

你至少要装：

- JDK 17
- Nginx
- MySQL
- Redis

注意：

- 具体安装命令和你选择的 Linux 发行版有关
- 如果你用的是 Alibaba Cloud Linux 3，可以优先按该系统的官方包管理方式安装
- 如果你用 Ubuntu，就按 Ubuntu 的包管理方式安装

不要死记命令，先记住目的：

- Java 负责跑后端
- MySQL 负责存数据
- Redis 负责缓存
- Nginx 负责对外入口

### 5.5 做完后怎么验证

你应该能确认：

- 能 SSH 登录服务器
- 能看到上面那几个目录
- `java -version` 正常
- `nginx -v` 正常
- `mysql --version` 正常
- `redis-server --version` 正常

---

## 6. 第三步：部署 MySQL

### 6.1 为什么这一步很关键

很多项目不是先死在 Java，而是先死在数据库。

因为：

- 查询慢
- 没索引
- 连接太多
- 日志没开

### 6.2 第一次部署你该怎么做

#### 步骤 1：创建数据库和账号

建议：

- 数据库名单独建一个
- 不要一直用 root 跑应用

你至少要有：

- 一个数据库
- 一个业务账号
- 一个强密码

#### 步骤 2：导入表结构

从这里找 SQL：

- `[sky.sql](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/resources/db/sky.sql)`

导入前你要做一件事：

- 先检查里面有没有你已经改过的字段和业务数据

如果线上要保留自己的最新结构，不要盲目全量覆盖。

#### 步骤 3：先把慢查询打开

生产前就要开，不要出问题了才开。

你需要开启：

- `slow_query_log = 1`
- `long_query_time = 1`

含义：

- 超过 1 秒的 SQL 都记录下来

### 6.3 你必须重点检查的索引

建议你逐张表检查。

#### 商品相关

推荐关注：

- `product(category_id, status)`
- `product(name)`
- `product(update_time)`

#### 订单相关

推荐关注：

- `orders(user_id, status, order_time)`
- `orders(number)`
- `orders(status, order_time)`

#### 明细和购物车

推荐关注：

- `order_detail(order_id)`
- `shopping_cart(user_id)`
- `stock(product_id)`

### 6.4 你可以怎么核查索引

先执行：

```sql
SHOW INDEX FROM product;
SHOW INDEX FROM orders;
SHOW INDEX FROM order_detail;
SHOW INDEX FROM shopping_cart;
SHOW INDEX FROM stock;
```

如果发现没有，就补。

例如：

```sql
CREATE INDEX idx_product_category_status ON product(category_id, status);
CREATE INDEX idx_orders_user_status_time ON orders(user_id, status, order_time);
CREATE INDEX idx_order_detail_order_id ON order_detail(order_id);
CREATE INDEX idx_shopping_cart_user_id ON shopping_cart(user_id);
CREATE INDEX idx_stock_product_id ON stock(product_id);
```

### 6.5 做完后怎么验证

你需要确认：

1. 后端能连上 MySQL
2. 核心表存在
3. 索引存在
4. 慢查询日志已开启

### 6.6 这一小步你学到什么

> 并发上来以后，数据库最怕的不是“数据多”，而是“没有索引还在反复扫表”。

---

## 7. 第四步：部署 Redis

### 7.1 为什么 Redis 对你很重要

你的项目想扛 `100+` 并发，Redis 几乎是必须的。

因为它能把这类查询从 MySQL 挪走：

- 分类列表
- 商品列表
- 机型列表
- 商品位置
- 店铺状态

### 7.2 你现在已经有的缓存基础

你不是从 0 开始。

当前已接入的位置：

- `[CacheConfig.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/config/CacheConfig.java)`
- `[ProductServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/ProductServiceImpl.java)`
- `[PhoneModelServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/PhoneModelServiceImpl.java)`
- `[ProductLocationServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/ProductLocationServiceImpl.java)`

你要学会判断：

- 不是所有接口都要缓存
- 优先缓存“读多写少”的接口

### 7.3 Redis 第一次部署要做什么

#### 步骤 1：设置密码

不要让 Redis 裸奔。

#### 步骤 2：限制监听范围

优先只允许：

- 本机访问
- 内网访问

#### 步骤 3：设定内存策略

建议先限制内存，再配置淘汰策略。

比如：

- `maxmemory`
- `maxmemory-policy allkeys-lru`

### 7.4 你还应该补的缓存点

当前建议继续补：

- 搜索热门词
- 后台统计接口
- 订单统计页短期缓存

第一阶段不要缓存：

- 提交订单结果
- 用户购物车写操作结果
- 库存扣减结果

### 7.5 做完后怎么验证

你需要确认：

1. Redis 有密码
2. 后端能正常连上 Redis
3. 商品列表查询命中缓存
4. 商品修改后缓存能失效

---

## 8. 第五步：部署后端 jar

### 8.1 你应该采用什么方式

对你现在最简单、最稳的方式是：

- 本地打包
- 上传 jar 到服务器
- 用 `systemd` 托管

这是最适合学习的方式。

### 8.2 打包前你要知道

你的项目根模块在：

- `[pom.xml](/F:/java/dlivery-project/sky-take-out/pom.xml)`

你真正运行的是：

- `sky-server`

### 8.3 你可以按这个思路做

#### 步骤 1：本地打包

在项目根目录执行：

```bash
mvn clean package -DskipTests
```

如果你想更明确只打后端，也可以按你的本地习惯执行对应模块打包。

#### 步骤 2：把 jar 上传到服务器

上传到：

- `/opt/mall/app/`

#### 步骤 3：把生产配置准备好

建议放到：

- `/opt/mall/config/application-prod.yml`

#### 步骤 4：用 systemd 托管

建议你创建：

- `/etc/systemd/system/mall.service`

核心思想：

- 开机自启
- 崩了自动重启
- 统一管理日志和状态

你可以参考这个结构：

```ini
[Unit]
Description=mall backend service
After=network.target

[Service]
User=root
WorkingDirectory=/opt/mall/app
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/mall/app/sky-server.jar --spring.profiles.active=prod --spring.config.additional-location=/opt/mall/config/
SuccessExitStatus=143
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

然后执行：

```bash
sudo systemctl daemon-reload
sudo systemctl enable mall
sudo systemctl start mall
sudo systemctl status mall
```

### 8.4 做完后怎么验证

你要验证：

1. `systemctl status mall` 是 `active`
2. `curl http://127.0.0.1:8080/...` 能访问接口
3. 应用日志正常写入
4. 重启服务器后应用会自动起来

### 8.5 这一小步你学到什么

> 生产环境跑 Java 服务，不是靠你手工执行一次 `java -jar`，而是要让系统帮你托管它。

---

## 9. 第六步：部署 web 管理端

### 9.1 你要理解一件事

管理端前端和后端不是一个东西：

- 后端：Java 服务
- 前端：打包后的静态文件

### 9.2 你的管理端项目位置

- `[project-sky-admin-vue-ts](/F:/java/dlivery-project/project-sky-admin-vue-ts)`

### 9.3 你要怎么做

#### 步骤 1：修改生产接口地址

你要确认管理端生产环境请求的是正式域名，不是本地地址。

#### 步骤 2：打包

在管理端项目里执行你当前项目对应的打包命令。

通常是类似：

```bash
npm install
npm run build
```

#### 步骤 3：把打包产物上传到 Nginx 静态目录

例如：

- `/usr/share/nginx/html/admin`

### 9.4 Nginx 怎么配

你的思路应该是：

- `/` 或 `/admin` 给前端页面
- `/api` 反向代理到 `127.0.0.1:8080`

示意：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        root /usr/share/nginx/html/admin;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 9.5 做完后怎么验证

你要确认：

1. 域名能打开后台页面
2. 登录请求能打到后端
3. 刷新页面不 404
4. 控制台没有接口跨域或 502

---

## 10. 第七步：处理小程序正式上线

这是你很容易忽略，但正式上线必须做的。

### 10.1 你现在开发阶段的问题

开发时你可以用：

- 本地地址
- 开发者工具里“不校验合法域名”

但正式上线不行。

### 10.2 你要改什么

你要确认小程序正式请求地址改成：

- 正式域名
- HTTPS

相关位置你自己重点看：

- `[env.js](/F:/java/dlivery-project/project-rjwm-weixin-uniapp-develop-wsy/utils/env.js)`
- `[pages/api/api.js](/F:/java/dlivery-project/project-rjwm-weixin-uniapp-develop-wsy/pages/api/api.js)`

### 10.3 你要做哪些平台配置

在小程序平台后台，你至少要配置：

- request 合法域名
- uploadFile 合法域名
- downloadFile 合法域名

学习重点：

- 小程序线上请求不能再依赖 `localhost`
- 域名通常要走 HTTPS
- 域名要和你真正请求的地址一致

### 10.4 做完后怎么验证

你要在真机验证：

1. 商品列表正常加载
2. 搜索正常
3. 下单正常
4. 图片正常显示
5. 没有“域名不合法”报错

---

## 11. 第八步：把当前项目继续做成“能扛并发”

这一部分是核心学习区。

---

## 12. 优化一：继续补缓存

### 12.1 先知道什么叫缓存

缓存的本质：

> 把“频繁读取、变化不快”的结果暂时存起来，下次不要再查数据库。

### 12.2 你当前已经有的缓存

你已经有：

- 商品分类相关缓存
- 商品列表缓存
- 机型缓存
- 商品位置缓存

### 12.3 你现在最值得补的两个缓存

#### 搜索结果缓存

为什么值得补：

- 搜索是热点接口
- 容易频繁模糊查询数据库

建议你检查并考虑补在：

- `[ProductServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/ProductServiceImpl.java)`

你要学的不是“直接复制注解”，而是理解：

- 同一个关键词短时间内重复搜索，结果大概率一样
- 所以可以短时间缓存，比如几分钟

#### 后台统计接口缓存

为什么值得补：

- 首页统计和图表往往会反复刷新
- 但几秒到几分钟内没必要次次扫库

你需要重点看：

- 订单统计
- 数据概览
- 图表统计

### 12.4 缓存要注意什么

最容易犯的错有两个：

1. 只会加缓存，不会删缓存
2. 把不该缓存的写操作也缓存了

你每加一个缓存，都要同时想：

- 什么时候更新
- 什么时候删除
- 最长能接受几分钟不一致

---

## 13. 优化二：检查最容易慢的 SQL

### 13.1 先看哪些接口

先盯这几条：

- 商品列表
- 商品搜索
- 购物车列表
- 提交订单
- 用户历史订单
- 后台订单分页

### 13.2 怎么找慢 SQL

方法很简单：

1. 开慢查询日志
2. 压测或人工频繁操作
3. 看哪条 SQL 出现次数最多、时间最长

### 13.3 你现在项目里需要重点留意的地方

#### 商品搜索

位置：

- `[ProductServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/ProductServiceImpl.java)`

风险：

- 搜索之后还会查规格
- 还会查适配机型
- 如果结果很多，就会产生多次附加查询

你要学会判断：

- 返回 10 条结果时，这个接口到底查了几次数据库

#### 订单查询

位置：

- `[OrderServiceImpl.java](/F:/java/dlivery-project/sky-take-out/sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java)`

好消息：

- 你的历史订单分页已经是“先批量查订单，再批量查明细，再组装”

这说明你已经比很多初学项目更好。

你下一步主要是：

- 继续看订单后台分页 SQL 是否有索引
- 看统计类查询是否扫表太大

---

## 14. 优化三：限制不必要的资源消耗

### 14.1 JVM

对 `2C4G` 服务器，不要太贪心。

推荐起步：

```bash
-Xms512m -Xmx1024m
```

先别一上来开更大。

因为你还要给：

- MySQL
- Redis
- Nginx
- Linux 系统

留内存。

### 14.2 数据库连接池

建议起步不要开太大。

你可以先从：

- `maximum-pool-size: 15`
- `minimum-idle: 5`

开始。

你要理解：

- 连接池开大，不代表性能就更好
- 如果 SQL 本身慢，只会让更多慢请求一起压数据库

### 14.3 Tomcat 线程

同理，不要迷信高线程数。

先从：

- `max: 80`
- `accept-count: 100`

开始。

---

## 15. 优化四：把日志和监控补齐

### 15.1 你至少要看哪些日志

#### Nginx 日志

看：

- 访问量
- 404
- 502
- 5xx

#### 应用日志

看：

- 登录失败
- 下单失败
- 库存扣减失败
- 订单状态流转异常

#### MySQL 慢查询日志

看：

- 哪些 SQL 最慢
- 哪些 SQL 最常出现

### 15.2 你至少要盯哪些系统指标

- CPU
- 内存
- 磁盘
- 网络带宽
- JVM 堆
- Redis 内存
- MySQL 活跃连接

### 15.3 最简单的学习方式

第一阶段不一定要上很重的监控系统。

你可以先学会这些基础命令和观察方式：

- `top`
- `htop`
- `free -m`
- `df -h`
- `ss -lntp`
- `tail -f`

先把“会看”学会，再谈复杂监控平台。

---

## 16. 第九步：做压测

这是你判断“能不能扛 100+ 并发”的最后一步。

### 16.1 不要一上来就测 100

正确顺序：

1. `30`
2. `50`
3. `80`
4. `100`
5. `120`

每升一档，都记录结果。

### 16.2 压测什么接口

第一批只压 6 个：

- 商品列表
- 搜索
- 加入购物车
- 购物车列表
- 提交订单
- 后台订单分页

### 16.3 压测时你要记录什么

每次压测，都记录：

- 并发数
- 总请求数
- 平均响应时间
- P95
- 错误率
- CPU
- 内存
- 慢查询数量

### 16.4 你该怎么理解结果

#### 如果 CPU 先满了

说明：

- Java 线程太忙
- 可能接口逻辑重
- 也可能 JSON/排序/组装过多

#### 如果 MySQL 先爆了

说明：

- SQL 慢
- 索引不够
- 缓存不够

#### 如果 Redis 命中率很低

说明：

- 你虽然接了 Redis，但并没有真的挡住热点流量

#### 如果 502/超时多

说明：

- 后端扛不住了
- 或 Nginx / 应用超时参数不合适

### 16.5 你要学的核心思维

> 压测不是为了“跑一个数字”，而是为了找到第一个真正的瓶颈。

---

## 17. 建议你按这个节奏实施

如果你一个人做，我建议分 7 天左右推进。

### 第 1 天

- 拆生产配置
- 建生产 profile
- 准备服务器目录结构

### 第 2 天

- 配安全组
- 安装 Java / Nginx / MySQL / Redis
- 验证基础环境

### 第 3 天

- 导入数据库
- 配 Redis
- 后端 jar 部署

### 第 4 天

- 管理端部署
- Nginx 反向代理
- 域名访问验证

### 第 5 天

- 小程序正式域名切换
- 真机联调

### 第 6 天

- 索引核查
- 搜索与统计缓存补齐
- 慢查询日志检查

### 第 7 天

- 压测
- 记录结果
- 针对瓶颈做第一轮优化

---

## 18. 最后的上线验收清单

上线前，你至少要逐项打勾。

### 基础可用

- 后端已用 `prod` 配置启动
- 管理端可正常登录
- 小程序真机可访问正式接口
- Redis 正常连接
- MySQL 正常连接
- Nginx 正常转发

### 基础安全

- `22` 端口未对全网开放
- `3306` 未对全网开放
- `6379` 未对全网开放
- 敏感密钥未继续写死在公开配置
- 生产环境不暴露调试入口

### 基础性能

- 关键表索引已核查
- 商品列表缓存正常
- 搜索接口已限制结果数量
- 慢查询日志已开启
- JVM 参数已配置

### 基础运维

- 应用由 `systemd` 托管
- 应用开机自启
- 日志路径清晰
- 有可回滚的 jar 包

### 并发验证

- 已做 30 / 50 / 80 / 100 并发测试
- 已记录 CPU、内存、慢 SQL
- 已知道系统第一个瓶颈在哪里

---

## 19. 对你当前项目的最优先建议

如果你只想抓重点，就按下面这个顺序做。

### 第一优先级

1. 生产配置拆分
2. Nginx 反向代理
3. MySQL 索引核查
4. Redis 联通与缓存验证
5. 后端 `systemd` 托管

### 第二优先级

1. 小程序正式域名切换
2. 慢查询日志
3. 搜索缓存
4. 统计缓存
5. 压测

### 第三优先级

1. 监控面板
2. 登录限流加强
3. OSS/CDN 优化
4. 拆库拆服务前的架构整理

---

## 20. 你做这件事时该怎么学习

最后给你一个很重要的建议。

不要把这件事理解成“我要一次学会部署、数据库、缓存、Linux、Nginx、压测”。

更好的学法是：

### 学法 1：每一步只学一个核心问题

例如：

- 配置隔离：学“为什么要区分 dev/prod”
- 安全组：学“为什么 3306 不能裸奔”
- Redis：学“为什么缓存可以减数据库压力”
- 索引：学“为什么按 user_id 查询必须建索引”
- 压测：学“为什么 100 并发不等于 100 个人同时点一下”

### 学法 2：每做完一步都自己回答这 3 个问题

1. 这一步解决了什么问题？
2. 如果不做，会出什么风险？
3. 我现在怎么验证它已经做好了？

### 学法 3：始终保留“记录”

建议你自己做一份小表格，记录：

- 做了什么
- 改了哪些文件
- 执行了哪些命令
- 出了什么错
- 最后怎么解决

这会极大提升你真正掌握项目上线的能力。

---

## 21. 结论

如果你按这份指南一步一步推进，你最后会真正掌握三件事：

1. 怎么把一个 Spring Boot + Vue + 小程序项目上线
2. 怎么把一个单机项目优化到能扛 `100+` 并发的水平
3. 怎么用“配置、缓存、索引、Nginx、压测”这五个抓手去判断系统是否健康

你现在最该做的，不是继续补零碎功能，而是：

1. 先把生产配置和服务器部署跑通
2. 再把索引和缓存补齐
3. 最后用压测去验证

只要你做到这三步，项目就真正进入“可上线”的阶段了。
