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
    BatchAnalyzeRequest, BatchAnalyzeResponse,
    # 本地模型 (V1.0)
    LocalSentimentRequest, LocalSentimentResponse,
    LocalSentimentBatchRequest, LocalSentimentBatchResponse,
    LocalSentimentStatsRequest, LocalSentimentStatsResponse,
)
from app.services.review_analysis_service import review_analysis_service
from app.services.local_sentiment_service import local_sentiment_service

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


# =====================================================
# 本地模型端点 (V1.0) — 使用自己训练的 RoBERTa 模型
# 免费、低延迟、三维度（整体/服务/菜品）情感分类
# =====================================================

@router.get("/local/health")
async def local_model_health():
    """检查本地模型是否已加载"""
    return {
        "available": local_sentiment_service.is_available,
        "model_info": local_sentiment_service.model_info if local_sentiment_service.is_available else None,
    }


@router.post(
    "/local/sentiment",
    response_model=LocalSentimentResponse,
    response_model_by_alias=False,
)
async def local_sentiment(request: LocalSentimentRequest):
    """
    使用本地模型分析单条评价。

    返回三维度情感：整体(overall)、服务(service)、菜品(dish)
    每维度输出四分类标签 + 置信度：
      - 0 未提及 / 1 负向 / 2 中性 / 3 正向
    """
    if not request.content or not request.content.strip():
        raise HTTPException(status_code=422, detail="评价内容不能为空")

    if not local_sentiment_service.is_available:
        raise HTTPException(
            status_code=503,
            detail="本地模型未加载，请检查模型文件是否存在"
        )

    logger.info(f"本地分析评价 reviewId={request.review_id}")
    try:
        result = local_sentiment_service.predict(
            text=request.content,
            review_id=request.review_id,
            merchant_id=request.merchant_id,
        )
        return LocalSentimentResponse(**result)
    except Exception as e:
        logger.error(f"本地分析失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/local/sentiment/batch")
async def local_sentiment_batch(request: LocalSentimentBatchRequest):
    """
    批量分析 — 使用本地模型。

    返回每条评价的三维度情感结果 + 整体统计聚合。
    单次最多 500 条。
    """
    if not request.reviews:
        raise HTTPException(status_code=422, detail="评价列表不能为空")

    if len(request.reviews) > 500:
        raise HTTPException(status_code=422, detail="单次最多分析500条评价")

    if not local_sentiment_service.is_available:
        raise HTTPException(status_code=503, detail="本地模型未加载")

    logger.info(f"本地批量分析 {len(request.reviews)} 条评价")

    reviews_dict = [
        {
            "review_id": r.review_id,
            "merchant_id": r.merchant_id,
            "content": r.content,
            "created_at": r.created_at,
        }
        for r in request.reviews
    ]

    try:
        predictions = local_sentiment_service.predict_batch(reviews_dict)
        from app.services.local_sentiment_service import aggregate_stats
        stats = aggregate_stats(predictions)
    except Exception as e:
        logger.error(f"本地批量分析失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))

    results = [LocalSentimentResponse(**p) for p in predictions]
    return {
        "successCount": len(results),
        "failCount": 0,
        "results": results,
        "stats": stats,
    }


@router.post("/local/sentiment/stats")
async def local_sentiment_stats(request: LocalSentimentStatsRequest):
    """
    统计聚合 — 对一批评价做情感分布统计。

    支持按时间（月度）、菜品、服务维度分别统计，
    返回每个维度的正向/负向/中性/未提及的数量和占比。
    这正是需求文档要求的"按时间、菜品、服务维度分别统计情感分布"。
    """
    if not request.reviews:
        raise HTTPException(status_code=422, detail="评价列表不能为空")

    if len(request.reviews) > 1000:
        raise HTTPException(status_code=422, detail="单次最多统计1000条评价")

    if not local_sentiment_service.is_available:
        raise HTTPException(status_code=503, detail="本地模型未加载")

    logger.info(f"统计聚合 {len(request.reviews)} 条评价")

    reviews_dict = [
        {
            "review_id": r.review_id,
            "merchant_id": r.merchant_id,
            "content": r.content,
            "created_at": r.created_at,
        }
        for r in request.reviews
    ]

    try:
        stats = local_sentiment_service.compute_stats(reviews_dict)
        return stats
    except Exception as e:
        logger.error(f"统计聚合失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ---- 以下为后续 Sprint 接口骨架，仅定义路由签名 ----

@router.post("/rag/recommend")
async def rag_recommend(request: dict):
    """RAG 推荐（后续实现）"""
    raise HTTPException(status_code=501, detail="RAG推荐功能尚未实现")


@router.post("/reviews/summary")
async def generate_summary(merchantId: int):
    """评价智能总结（后续实现）"""
    raise HTTPException(status_code=501, detail="评价总结功能尚未实现")


@router.get("/hot-words/{region}")
async def regional_hot_words(region: str, days: int = 7):
    """区域热词（后续实现）"""
    raise HTTPException(status_code=501, detail="区域热词功能尚未实现")