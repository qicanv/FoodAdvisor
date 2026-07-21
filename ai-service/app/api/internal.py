"""
FastAPI 内部接口 — 供 Spring Boot 后端调用

包含：
- POST /internal/test                      连接测试
- POST /internal/reviews/analyze           单条评价分析
- POST /internal/reviews/batch-analyze     批量分析
- POST /internal/content/process           内容清洗与切分
- POST /internal/content/query             查询/导出处理结果
- POST /internal/knowledge/upsert          知识向量化与存储
- POST /internal/knowledge/deactivate      停用知识文档
- POST /internal/search/semantic                    语义检索
- POST /internal/reviews/summary-faithfulness-test  摘要忠实性测试（EPIC-06 S3，LLM-as-Judge）
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
from app.schemas.knowledge import (
    KnowledgeUpsertRequest, KnowledgeUpsertResponse,
    KnowledgeDeactivateRequest, KnowledgeDeactivateResponse,
)
from app.services.dialogue_extraction_service import dialogue_extraction_service
from app.services.review_analysis_service import review_analysis_service
from app.models.schemas import ReviewSummaryRequest, ReviewSummaryResponse
from app.services.review_summary_service import review_summary_service
from app.services.content_processing_service import content_processing_service
from app.services.knowledge_service import get_knowledge_service
from app.schemas.search import SearchRequest, SearchResponse
from app.services.search_service import get_search_service
from app.models.schemas import HighlightGenerateRequest, HighlightGenerateResponse
from app.services.highlight_service import highlight_service
from app.models.schemas import GenerateReplyRequest, GenerateReplyResponse
from app.services.reply_draft_service import reply_draft_service
from app.models.schemas import CompetitorComparisonRequest, CompetitorComparisonResponse
from app.services.competitor_comparison_service import competitor_comparison_service
from app.models.schemas import FaithfulnessTestRequest, FaithfulnessTestResponse
from app.services.faithfulness_test_service import faithfulness_test_service

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


# ---- 知识向量化与存储 ----

@router.post(
    "/knowledge/upsert",
    response_model=KnowledgeUpsertResponse,
    summary="知识向量化与存储",
)
async def upsert_knowledge(request: KnowledgeUpsertRequest):
    """
    将清洗后的文本块转换为向量并写入 OpenSearch。

    流程：
    1. 确保索引存在并校验向量维度
    2. 逐条对比 contentHash，跳过内容未变化的文档
    3. 对新增/变更文档批量调用 Embedding 模型生成向量
    4. 以 chunkId 为 _id 幂等写入 OpenSearch

    单条失败不影响其他文档；相同 chunkId + contentHash 重复请求时
    标记为 SKIPPED。
    """
    if not request.documents:
        raise HTTPException(status_code=422, detail="文档列表不能为空")
    if len(request.documents) > 500:
        raise HTTPException(status_code=422, detail="单次最多处理500条文档")

    logger.info(
        "知识向量化请求: documents=%d",
        len(request.documents),
    )

    service = get_knowledge_service()
    result = service.upsert(request)

    logger.info(
        "知识向量化完成: total=%d, success=%d, skipped=%d, failed=%d",
        result.total,
        result.successCount,
        result.skipCount,
        result.failCount,
    )
    return result


@router.post(
    "/knowledge/deactivate",
    response_model=KnowledgeDeactivateResponse,
    summary="停用知识文档",
)
async def deactivate_knowledge(request: KnowledgeDeactivateRequest):
    """
    批量停用知识文档（将 isActive 设为 false），使其不再参与公开检索。

    - sourceType=MERCHANT：停用该商家下的全部文档（介绍+菜品+评价）
    - sourceType=MERCHANT_INTRO / MENU / REVIEW：精确停用匹配的文档

    停用的文档在 OpenSearch 中保留，可后续恢复。
    """
    if not request.sourceIds:
        raise HTTPException(status_code=422, detail="sourceIds 不能为空")
    if len(request.sourceIds) > 500:
        raise HTTPException(status_code=422, detail="单次最多停用500个 ID")

    logger.info(
        "停用知识文档: sourceType=%s, sourceIds=%s",
        request.sourceType,
        request.sourceIds[:10],
    )

    service = get_knowledge_service()
    result = service.deactivate(request)

    logger.info(
        "停用完成: sourceType=%s, deactivated=%d",
        request.sourceType,
        result.deactivatedCount,
    )
    return result


# ---- 语义检索 ----

@router.post(
    "/search/semantic",
    response_model=SearchResponse,
    summary="语义检索",
)
async def semantic_search(request: SearchRequest):
    """
    将用户查询转换为向量，从 OpenSearch 中检索最相关的知识文档。

    流程：
    1. 将查询文本通过 BGE 模型转为 768 维查询向量（带指令前缀）
    2. 在 OpenSearch 中执行 k-NN 向量检索
    3. 过滤 isActive=true + 可选的 merchantIds/sourceTypes
    4. 返回 Top-K 相关文档及相似度分数

    OpenSearch 不可用时返回 searchMode=KEYWORD_FALLBACK。
    """
    if not request.query.strip():
        raise HTTPException(status_code=422, detail="查询文本不能为空")

    logger.info(
        "语义检索: query='%s', topK=%d",
        f"<redacted:length={len(request.query)}>",
        request.topK,
    )

    service = get_search_service()
    result = service.search(request)

    logger.info(
        "语义检索完成: mode=%s, results=%d",
        result.data.searchMode,
        len(result.data.results),
    )
    return result


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


# ---- 评价辅助回复（EPIC-02 故事7） ----

@router.post(
    "/reviews/generate-reply",
    response_model=GenerateReplyResponse,
)
async def generate_review_reply(request: GenerateReplyRequest):
    """
    评价辅助回复生成（EPIC-02 故事7）

    由 Spring Boot 传入评价内容和策略（POSITIVE/NEGATIVE），
    返回 AI 生成的回复建议。商家确认或编辑后才能发布为正式回复。

    好评策略：表达感谢 + 回应具体优点
    差评策略：道歉 + 问题说明 + 改进承诺
    """
    logger.info(
        f"生成评价回复 reviewId={request.reviewId}, "
        f"strategy={request.strategy.value}, rating={request.rating}"
    )
    return await reply_draft_service.generate(request)


# ---- 周边竞品对比（EPIC-02 Story 6） ----

@router.post(
    "/merchants/competitor-comparison",
    response_model=CompetitorComparisonResponse,
)
async def generate_competitor_comparison(request: CompetitorComparisonRequest):
    """
    周边竞品对比分析（EPIC-02 Story 6）

    由 Spring Boot 传入本店和竞品的统计数据，AI 生成：
    - 每家商家的优势/短板分析
    - 横向对比总结（本店在竞品中的定位）
    - 基于数据差距的改进建议

    验收准则对齐：
    1. 对比维度至少包括价格、评分、好评率、评价数量（由 Spring Boot 提供）
    2. 明显差异时突出优势/短板，无明显差异时明确说明
    3. 所有分析基于传入的真实统计数据，AI 不编造数值
    4. 竞品数量限制 2~4 家（含本店）
    """
    if len(request.competitors) < 2:
        raise HTTPException(
            status_code=422,
            detail="至少需要本店 + 1家竞品（共2家）才能进行对比"
        )
    if len(request.competitors) > 4:
        raise HTTPException(
            status_code=422,
            detail="最多支持4家商家（本店 + 最多3家竞品）"
        )

    # 校验本店在列表中
    self_ids = [c.merchantId for c in request.competitors if c.merchantId == request.merchantId]
    if not self_ids:
        raise HTTPException(
            status_code=422,
            detail="竞品列表中必须包含本店（merchantId 与请求中的 merchantId 一致）"
        )

    logger.info(
        f"竞品对比分析 merchantId={request.merchantId}, "
        f"competitorCount={len(request.competitors) - 1}"
    )
    return await competitor_comparison_service.compare(request)


# ---- 评论摘要忠实性测试（EPIC-06 Story 3） ----

@router.post(
    "/reviews/summary-faithfulness-test",
    response_model=FaithfulnessTestResponse,
)
async def test_summary_faithfulness(request: FaithfulnessTestRequest):
    """
    评论摘要忠实性测试（EPIC-06 Story 3）

    采用 LLM-as-Judge 模式，对已生成的商家口碑摘要进行忠实性验证。
    将摘要中的每个声明（优点/不足/推荐菜/环境/服务/近期变化/总结文本）
    与引用的原始评价原文进行对比，判断声明是否忠实于评价内容。

    输入：
    - summary: 已生成的 ReviewSummaryResponse（商家口碑摘要）
    - reviews: 参与生成该摘要的原始评价列表（必须包含原文内容）

    输出：
    - overallScore: 整体忠实性得分（FAITHFUL 占比，0~1）
    - claimResults: 每个声明的详细判定结果（FAITHFUL/UNFAITHFUL/UNCERTAIN +
      置信度 + 详细理由）
    - 各类计数统计（faithfulCount / unfaithfulCount / uncertainCount）

    验收准则对齐：
    1. 摘要中的每个事实性声明都能追溯到原始评价并验证一致性
    2. 模型准确率按月记录，低于阈值时触发优化流程
    3. 整体忠实性得分 < 0.8 时 testStatus="PARTIAL"，前端可据此展示告警
    4. 未匹配到原文的声明标记为 UNCERTAIN（信息不足）
    5. 服务不可用或 LLM 调用失败时返回 testStatus="FAILED"
    """
    if not request.reviews:
        raise HTTPException(status_code=422, detail="评价列表不能为空")

    if not request.summary:
        raise HTTPException(status_code=422, detail="摘要结果不能为空")

    logger.info(
        f"摘要忠实性测试 merchantId={request.merchantId}, "
        f"reviewCount={len(request.reviews)}, "
        f"summaryStatus={request.summary.summaryStatus}"
    )

    # 摘要本身状态不是 SUCCESS 时，跳过测试直接返回
    if request.summary.summaryStatus != "SUCCESS":
        logger.info(
            f"摘要状态为 {request.summary.summaryStatus}，跳过忠实性测试 "
            f"merchantId={request.merchantId}"
        )
        return FaithfulnessTestResponse(
            merchantId=request.merchantId,
            testStatus="SUCCESS",
            overallScore=1.0,
            totalClaims=0,
            faithfulCount=0,
            unfaithfulCount=0,
            uncertainCount=0,
            claimResults=[],
            summaryText=request.summary.summaryText,
            modelName=None,
            businessTraceId=None,
            errorMessage=f"摘要状态为 {request.summary.summaryStatus}，无需测试",
        )

    return await faithfulness_test_service.test(request)
