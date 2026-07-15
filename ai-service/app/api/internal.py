from fastapi import APIRouter, Depends

from app.core.security import verify_internal_token
from app.schemas.common import InternalResponse, InternalTestRequest

router = APIRouter(
    prefix="/internal",
    tags=["Internal"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post(
    "/test",
    response_model=InternalResponse,
    response_model_by_alias=True,
)
def internal_test(
    request: InternalTestRequest,
) -> InternalResponse:
    return InternalResponse(
        request_id=request.request_id,
        status="SUCCESS",
        data={
            "message": "AI service connected",
            "echo": request.message,
        },
    )