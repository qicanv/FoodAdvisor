from fastapi import FastAPI

from app.api.health import router as health_router
from app.api.internal import router as internal_router
from app.core.config import settings
from app.core.exceptions import register_exception_handlers


def create_app() -> FastAPI:
    application = FastAPI(
        title=settings.app_name,
        version="0.1.0",
        description="FoodAdvisor 的 AI、RAG 和评论分析服务",
        debug=settings.app_debug,
    )

    application.include_router(health_router)
    application.include_router(internal_router)

    register_exception_handlers(application)

    return application


app = create_app()