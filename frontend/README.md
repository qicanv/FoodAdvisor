# FoodAdvisor Frontend

FoodAdvisor 智能餐厅推荐系统前端。

## 技术栈

- Node.js 24
- npm 11
- Vue 3
- Vite
- Vue Router
- Axios
- Element Plus
- ECharts

## 安装依赖

```cmd
npm install
````

## 启动开发服务

```cmd
npm run dev
```

访问：

```text
http://localhost:5173
```

## 构建生产版本

```cmd
npm run build
```

## 环境变量

复制环境变量模板：

```cmd
copy .env.example .env
```

默认服务地址：

```text
Spring Boot：http://localhost:8080
FastAPI：http://localhost:8000
```

## 当前页面

* `/`：系统首页
* `/restaurants`：餐厅列表

## 本地联调

先在项目根目录启动基础设施：

```cmd
docker compose up -d
```

启动后端：

```cmd
cd backend
mvn spring-boot:run
```

启动前端：

```cmd
cd frontend
npm run dev