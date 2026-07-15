from fastapi import Header, HTTPException, status

from app.core.config import settings


def verify_internal_token(
    x_internal_token: str | None = Header(
        default=None,
        alias="X-Internal-Token",
    ),
) -> None:
    if not settings.internal_api_token:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Internal API token is not configured",
        )

    if x_internal_token != settings.internal_api_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal token",
        )
