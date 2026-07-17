"""
FastAPI 内部接口 — 供 Spring Boot 后端调用

包含：
- POST /internal/test                      连接测试
- POST /internal/reviews/analyze           单条评价分析
- POST /internal/reviews/batch-analyze     批量分析
"""
import logging
from fastapi import APIRouter, Depends, HTTPException

from app.core.security import verify_internal_token
from app.schemas.common import InternalResponse, InternalTestRequest
from app.models.schemas import (
    AnalyzeRequest, AnalyzeResponse,
    BatchAnalyzeRequest, BatchAnalyzeResponse
)
from app.services.review_analysis_service import review_analysis_service
from app.models.schemas import ReviewSummaryRequest, ReviewSummaryResponse
from app.services.review_summary_service import review_summary_service

logger = logging.getLogger(__name__)

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


@router.post("/reviews/analyze", response_model=AnalyzeResponse)
async def analyze_review(request: AnalyzeRequest):
    """
    分析单条评价 — 情感分析 + 关键词 + 方面 + 差评归因

    供 Spring Boot 的 ReviewController 调用
    """
    if not request.content or not request.content.strip():
        raise HTTPException(status_code=422, detail="评价内容不能为空")

    logger.info(f"分析评价 reviewId={request.reviewId}, merchantId={request.merchantId}")
    result = await review_analysis_service.analyze(request)
    return result


@router.post("/reviews/batch-analyze", response_model=BatchAnalyzeResponse)
async def batch_analyze_reviews(request: BatchAnalyzeRequest):
    """
    批量分析评价
    """
    if not request.reviews:
        raise HTTPException(status_code=422, detail="评价列表不能为空")

    if len(request.reviews) > 100:
        raise HTTPException(status_code=422, detail="单次最多分析100条评价")

    logger.info(f"批量分析 {len(request.reviews)} 条评价")
    results, errors = await review_analysis_service.batch_analyze(request.reviews)

    return BatchAnalyzeResponse(
        successCount=len(results),
        failCount=len(errors),
        results=results,
        errors=errors
    )


# ---- 以下为后续 Sprint 接口骨架，仅定义路由签名 ----

@router.post("/rag/recommend")
async def rag_recommend(request: dict):
    """RAG 推荐（后续实现）"""
    raise HTTPException(status_code=501, detail="RAG推荐功能尚未实现")


@router.post("/merchants/review-summary", response_model=ReviewSummaryResponse)
async def generate_review_summary(request: ReviewSummaryRequest):
    """
    商家评价智能总结（EPIC-01 Story 7）

    由 Spring Boot 传入评论列表，返回结构化口碑摘要。
    评论不足时返回 summaryStatus=INSUFFICIENT_DATA，不调用大模型。
    """
    logger.info(
        f"生成评价摘要 merchantId={request.merchantId}, "
        f"reviewCount={len(request.reviews)}"
    )
    return await review_summary_service.summarize(request)


@router.get("/hot-words/{region}")
async def regional_hot_words(region: str, days: int = 7):
    """区域热词（后续实现）"""
    raise HTTPException(status_code=501, detail="区域热词功能尚未实现")