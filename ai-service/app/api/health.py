from datetime import datetime, timezone

from fastapi import APIRouter

from app.clients.opensearch_client import check_opensearch_connection

router = APIRouter(tags=["Health"])


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
            "modelApi": "NOT_CONFIGURED",
        },
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }