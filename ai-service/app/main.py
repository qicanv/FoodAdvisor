"""
FoodAdvisor AI 服务 — FastAPI 入口
"""
import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.internal import router as internal_router
from app.models.schemas import HealthResponse
from app.services.llm_service import llm_service
from app.config import AI_SERVICE_PORT, LLM_MODEL

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)

# 创建 FastAPI 应用
app = FastAPI(
    title="FoodAdvisor AI Service",
    description="智能探店系统 - AI 服务 (FastAPI)",
    version="0.1.0"
)

# CORS（允许同机开发调用）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(internal_router)


# ---- 健康检查 ----

@app.get("/health", response_model=HealthResponse)
async def health():
    """AI 服务健康检查"""
    from datetime import datetime, timezone
    deps = {"openSearch": "UNKNOWN", "modelApi": "UNKNOWN"}
    if llm_service.is_configured():
        deps["modelApi"] = "UP"
    return HealthResponse(
        service="ai-service",
        status="UP",
        dependencies=deps,
        timestamp=datetime.now(timezone.utc).isoformat()
    )


# ---- 启动 ----

if __name__ == "__main__":
    import uvicorn
    logger.info(f"启动 AI 服务，端口: {AI_SERVICE_PORT}, 模型: {LLM_MODEL}")
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=AI_SERVICE_PORT,
        reload=True
    )
