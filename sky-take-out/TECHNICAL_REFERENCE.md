# 电子产品便利店系统 - 技术参考手册

## 一、项目架构

### 1.1 模块结构
- **sky-common**：公共组件（常量、异常、工具类、配置属性）
- **sky-pojo**：数据传输对象（DTO、VO、Entity）
- **sky-server**：业务逻辑层（Controller、Service、Mapper）

### 1.2 技术栈
- **框架**：Spring Boot 2.x
- **ORM**：MyBatis
- **数据库**：MySQL
- **缓存**：Redis
- **文档**：Swagger/Knife4j
- **支付**：微信支付

---

## 二、核心API接口

### 2.1 订单管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/admin/order/conditionSearch` | GET | 条件搜索订单 |
| `/admin/order/statistics` | GET | 订单状态统计 |
| `/admin/order/details/{id}` | GET | 订单详情 |
| `/admin/order/confirm` | PUT | 确认订单 |
| `/admin/order/rejection` | PUT | 驳回订单 |
| `/admin/order/cancel` | PUT | 取消订单 |

### 2.2 商品管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/admin/product` | POST | 新增商品 |
| `/admin/product` | PUT | 修改商品 |
| `/admin/product` | DELETE | 批量删除商品 |
| `/admin/product/page` | GET | 分页查询 |
| `/admin/product/{id}` | GET | 商品详情 |
| `/admin/product/status/{status}` | POST | 启用/停售 |

### 2.3 手机型号管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/admin/phoneModel` | POST | 新增手机型号 |
| `/admin/phoneModel` | PUT | 修改手机型号 |
| `/admin/phoneModel` | DELETE | 批量删除 |
| `/admin/phoneModel/page` | GET | 分页查询 |
| `/admin/phoneModel/list` | GET | 查询所有启用型号 |
| `/user/product/listByPhoneModel` | GET | 按型号查询商品 |

### 2.4 数据统计

| 接口 | 方法 | 说明 |
|------|------|------|
| `/admin/statistics/turnover` | GET | 营业额统计 |
| `/admin/statistics/users` | GET | 用户统计 |
| `/admin/statistics/orders` | GET | 订单统计 |
| `/admin/statistics/top10` | GET | 销量TOP10 |

---

## 三、部署配置

### 3.1 JVM启动参数（低配服务器）
```bash
JAVA_OPTS="-Xms256m -Xmx512m \
-XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m \
-XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
-XX:+DisableExplicitGC \
-Dfile.encoding=UTF-8"

java $JAVA_OPTS -jar sky-server.jar
```

### 3.2 应用配置 (application.yml)
```yaml
server:
  tomcat:
    max-threads: 50
    min-spare-threads: 5

spring:
  datasource:
    hikari:
      maximum-pool-size: 8
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
```

### 3.3 MySQL配置优化 (my.cnf)
```ini
[mysqld]
max_connections = 100
table_open_cache = 256
tmp_table_size = 32M
thread_cache_size = 8
key_buffer_size = 128M
sort_buffer_size = 1M
innodb_buffer_pool_size = 256M
innodb_log_file_size = 64M
innodb_flush_log_at_trx_commit = 2
```



### 3.5 Nginx反向代理
```nginx
upstream backend {
    server localhost:8080 weight=1 max_fails=2 fail_timeout=30s;
}

server {
    listen 80;
    server_name your-domain.com;
    
    location ~* \.(jpg|jpeg|png|gif|ico|css|js)$ {
        root /var/www/static;
        expires 1d;
        add_header Cache-Control "public, immutable";
    }
    
    location / {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        gzip on;
        gzip_types text/plain text/css application/json application/javascript;
    }
}
```

### 3.6 Docker部署
```dockerfile
FROM openjdk:8-jre-alpine
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
WORKDIR /app
COPY sky-server/target/sky-server.jar app.jar
RUN addgroup -g 1001 -S spring && adduser -u 1001 -S spring -G spring
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
```

---

## 四、资源预估

| 指标 | 建议值 |
|------|--------|
| JVM堆内存 | 512MB |
| 非堆内存 | 128MB |
| 系统预留 | 256MB |
| 总内存 | 约900MB |
| 平均CPU | < 30% |
| 峰值CPU | < 70% |

---

## 五、待开发功能



### 5.2 备忘录功能
- 客户代办事项记录
- 智能文本解析
- 定时任务自动处理

### 5.3 数据可视化
- ECharts图表集成
- 商品销量排行

