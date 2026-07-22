import hmac

from fastapi import Header, HTTPException, status

from app.core.config import settings


def verify_internal_token(
    x_internal_token: str | None = Header(
        default=None,
        alias="X-Internal-Token",
    ),
) -> None:
    configured_token = (settings.internal_api_token or "").strip()

    # 服务端未配置 Token 时拒绝所有内部请求，避免默认放行。
    if not configured_token:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Internal API authentication is not configured",
        )

    provided_token = x_internal_token or ""

    if not hmac.compare_digest(provided_token, configured_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal token",
        )