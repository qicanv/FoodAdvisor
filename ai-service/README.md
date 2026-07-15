# FoodAdvisor AI Service

FoodAdvisor 项目的 AI、RAG、语义检索和评论分析服务。

## 技术栈

- Python 3.10
- FastAPI
- Pydantic
- OpenSearch
- Pytest

## 创建虚拟环境

```cmd
python -m venv .venv
.venv\Scripts\activate
````

## 安装依赖

```cmd
python -m pip install -r requirements.txt
```

## 配置环境变量

```cmd
copy .env.example .env
```

根据本地环境修改 `.env`。

## 启动服务

```cmd
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

## 接口

* `GET /health`
* `POST /internal/test`
* Swagger：`http://localhost:8000/docs`

调用内部接口时需要携带：

```http
X-Internal-Token: <INTERNAL_API_TOKEN>
```

## 运行测试

```cmd
python -m pytest -v
```

当前测试包括：

* 健康检查接口
* 内部接口正常调用
* 错误 Token 校验
* 空消息参数校验