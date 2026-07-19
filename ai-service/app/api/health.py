from datetime import datetime, timezone

from fastapi import APIRouter

from app.clients.opensearch_client import check_opensearch_connection
from app.core.config import settings

router = APIRouter(tags=["Health"])


def get_model_api_status() -> str:
    """判断模型 API 的必要配置是否完整，不进行真实网络调用。"""
    required_values = (
        settings.llm_api_key,
        settings.llm_base_url,
        settings.llm_model,
    )

    configured = all(
        isinstance(value, str) and bool(value.strip())
        for value in required_values
    )

    return "CONFIGURED" if configured else "NOT_CONFIGURED"


@router.get("/health")
def health_check() -> dict:
    opensearch_is_up = check_opensearch_connection()

    service_status = "UP" if opensearch_is_up else "DEGRADED"
    opensearch_status = "UP" if opensearch_is_up else "DOWN"

    return {
        "service": "ai-service",
        "status": service_status,
        "dependencies": {
            "openSearch": opensearch_status,
            "modelApi": get_model_api_status(),
        },
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
