"""
FastAPI 内部接口 — 供 Spring Boot 后端调用

包含：
- POST /internal/test                      连接测试
- POST /internal/reviews/analyze           单条评价分析
- POST /internal/reviews/batch-analyze     批量分析
- POST /internal/content/process           内容清洗与切分
- POST /internal/content/query             查询/导出处理结果
"""
import logging
from fastapi import APIRouter, Depends, HTTPException

from app.core.security import verify_internal_token
from app.schemas.common import InternalResponse, InternalTestRequest
from app.models.schemas import (
    AnalyzeRequest, AnalyzeResponse,
    BatchAnalyzeRequest, BatchAnalyzeResponse
)
from app.schemas.dialogue import DialogueExtractRequest, DialogueExtractResponse
from app.schemas.content_processing import (
    ProcessRequest, ProcessResult, QueryRequest,
)
from app.services.dialogue_extraction_service import dialogue_extraction_service
from app.services.review_analysis_service import review_analysis_service
from app.models.schemas import ReviewSummaryRequest, ReviewSummaryResponse
from app.services.review_summary_service import review_summary_service
from app.services.content_processing_service import content_processing_service
from app.models.schemas import HighlightGenerateRequest, HighlightGenerateResponse
from app.services.highlight_service import highlight_service

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


@router.post("/dialogue/extract", response_model=DialogueExtractResponse)
async def extract_dialogue_constraints(request: DialogueExtractRequest):
    if not request.content or not request.content.strip():
        raise HTTPException(status_code=422, detail="content must not be blank")

    return await dialogue_extraction_service.extract(request)


# ---- 内容清洗与切分 ----

@router.post(
    "/content/process",
    response_model=ProcessResult,
    summary="内容清洗与切分",
)
async def process_content(request: ProcessRequest):
    """
    对商家介绍、菜单描述和用户评价进行统一清洗和切分。

    清洗操作：
    - 去除 HTML 标签、控制字符、重复空格
    - 统一日期格式（YYYY-MM-DD）、价格格式（¥XX）
    - 去除无意义内容（如"该用户没有填写评价"）

    切分操作：
    - 按段落和句子边界智能切分
    - 超长文本保留上下文重叠
    - 每个文本块保留 merchantId、sourceType、sourceId、时间等来源信息

    容错：单条数据处理失败记录错误，不影响其他数据继续处理。
    确定性：相同输入和配置重复处理时得到一致的 chunkId。
    """
    if not request.items:
        raise HTTPException(status_code=422, detail="内容列表不能为空")
    if len(request.items) > 500:
        raise HTTPException(status_code=422, detail="单次最多处理500条内容")

    logger.info(
        "内容处理请求: items=%d, maxChunkLength=%d, overlap=%d",
        len(request.items),
        request.chunkConfig.maxChunkLength,
        request.chunkConfig.overlapLength,
    )
    result = content_processing_service.process(request)
    logger.info(
        "内容处理完成: success=%d, fail=%d, chunks=%d",
        result.successCount,
        result.failCount,
        result.totalChunks,
    )
    return result


@router.post(
    "/content/query",
    summary="查询/导出处理结果",
)
async def query_content(request: QueryRequest):
    """
    按 merchantId / sourceType / sourceId 查询处理前后的内容和切分结果。

    当前返回占位 — 后续可对接 OpenSearch 或数据库进行持久化查询。
    """
    raise HTTPException(status_code=501, detail="内容查询功能将在数据持久化后实现（可对接 OpenSearch）")


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


# ---- 商家亮点挖掘（EPIC-02 Story 5） ----

@router.post(
    "/merchants/highlights",
    response_model=HighlightGenerateResponse,
)
async def generate_merchant_highlights(request: HighlightGenerateRequest):
    """
    商家亮点挖掘（EPIC-02 Story 5）

    由 Spring Boot 传入正面评论列表，返回结构化亮点。
    正面评论不足时返回 highlightStatus=INSUFFICIENT_DATA，不调用大模型。
    """
    logger.info(
        f"生成商家亮点 merchantId={request.merchantId}, "
        f"positiveReviewCount={len(request.reviews)}"
    )
    return await highlight_service.generate(request)
