# FoodAdvisor Backend

FoodAdvisor 项目的业务后端服务。

## 技术栈

- Java 17
- Spring Boot 3
- Maven
- Spring Web
- Validation
- Spring Boot Actuator
- MyBatis-Plus
- PostgreSQL 16
- Redis 7
- Lombok
- JUnit 5
- MockMvc

## 环境要求

- Java 17
- Maven 3.9+
- Docker Desktop

## 启动基础设施

在项目根目录执行：

```cmd
docker compose up -d
````

检查：

```cmd
docker compose ps
```

需要确认：

* PostgreSQL healthy
* Redis healthy
* OpenSearch healthy

## 数据库配置

默认配置：

```text
数据库：foodadvisor
用户名：postgres
密码：password
端口：5432
```

Redis 默认端口：

```text
6379
```

## 启动后端

进入 backend 目录：

```cmd
mvn spring-boot:run
```

服务地址：

```text
http://localhost:8080
```

## 当前接口

### Actuator 健康检查

```http
GET /actuator/health
```

### 后端自定义健康检查

```http
GET /api/health
```

## 运行测试

```cmd
mvn clean test
```

当前测试包括：

* 后端健康接口测试
* 餐厅列表接口测试

