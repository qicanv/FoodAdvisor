"""
Pydantic 数据模型 — V0.3，与接口规范/数据库设计对齐

变更 (V0.2→V0.3):
- AnalyzeRequest 新增 reviewVersion
- AnalyzeResponse 新增 reviewVersion, analysisVersion, businessTraceId, errorMessage, startedAt, completedAt
- 新增 IssueCategoryResult：差评归因结构化返回
"""
from pydantic import BaseModel, Field
from typing import Optional, List
from enum import Enum


class SentimentEnum(str, Enum):
    POSITIVE = "POSITIVE"
    NEUTRAL = "NEUTRAL"
    NEGATIVE = "NEGATIVE"
    MIXED = "MIXED"


class AspectSentimentEnum(str, Enum):
    POSITIVE = "POSITIVE"
    NEGATIVE = "NEGATIVE"
    NEUTRAL = "NEUTRAL"


class AspectCategoryEnum(str, Enum):
    TASTE = "TASTE"
    ENVIRONMENT = "ENVIRONMENT"
    SERVICE = "SERVICE"
    PRICE = "PRICE"
    QUEUE_TIME = "QUEUE_TIME"
    HYGIENE = "HYGIENE"
    PORTION = "PORTION"
    SPEED = "SPEED"
    PARKING = "PARKING"
    OTHER = "OTHER"


class IssueCategoryEnum(str, Enum):
    """差评归因类别（与 review_issue_categories 表对齐）"""
    HYGIENE = "HYGIENE"
    SERVICE_ATTITUDE = "SERVICE_ATTITUDE"
    SERVING_SPEED = "SERVING_SPEED"
    TASTE = "TASTE"
    PRICE = "PRICE"
    PORTION = "PORTION"
    QUEUE = "QUEUE"
    ENVIRONMENT = "ENVIRONMENT"
    OTHER = "OTHER"


class AspectResult(BaseModel):
    """方面级情感分析结果"""
    category: str = Field(description="TASTE/ENVIRONMENT/SERVICE/PRICE/QUEUE_TIME/HYGIENE/PORTION/SPEED/PARKING/OTHER")
    sentiment: str = Field(description="POSITIVE/NEGATIVE/NEUTRAL")
    text: str = Field(description="该方面的原文片段")


class TagResult(BaseModel):
    """标签提取结果"""
    tagCode: str = Field(description="标签编码，如 TASTE_GOOD")
    tagName: str = Field(description="标签名称，如 口味好")
    category: str = Field(description="标签类别")
    sentiment: str = Field(description="POSITIVE/NEUTRAL/NEGATIVE")
    confidence: float = Field(ge=0, le=1)
    evidenceText: Optional[str] = Field(default=None, description="原文依据")


class IssueCategoryResult(BaseModel):
    """差评归因结果（V0.3 新增）"""
    category: str = Field(description="归因类别编码，如 HYGIENE, SERVICE_ATTITUDE")
    categoryName: str = Field(description="类别名称，如 卫生问题")
    confidence: float = Field(ge=0, le=1)
    evidenceText: Optional[str] = Field(default=None, description="原文依据片段")


class AnalyzeRequest(BaseModel):
    """单条评价分析请求（V0.3）"""
    reviewId: int
    merchantId: int
    reviewVersion: int = Field(default=1, ge=1, description="评价版本号")
    content: str = Field(..., min_length=1, description="评价原文内容")
    modelVersion: Optional[str] = Field(default=None)
    systemPrompt: Optional[str] = Field(
        default=None,
        max_length=50000,
        description="Spring Boot 解析出的运行时系统提示词",
    )
    promptVersion: Optional[str] = Field(
        default=None,
        max_length=255,
        description="本次请求使用的提示词版本标签",
    )


class AnalyzeResponse(BaseModel):
    """单条评价分析响应 — V0.3"""
    reviewId: int
    merchantId: int
    reviewVersion: int = Field(default=1, description="评价版本号")
    analysisVersion: int = Field(default=1, description="分析版本号")
    sentiment: str = Field(description="POSITIVE/NEUTRAL/NEGATIVE/MIXED")
    confidence: float = Field(ge=0, le=1, description="整体置信度")
    lowConfidence: bool = Field(default=False, description="是否低于置信度阈值")
    keywords: List[str] = Field(default_factory=list, description="关键词列表")
    aspects: List[AspectResult] = Field(default_factory=list, description="方面级情感")
    tags: List[TagResult] = Field(default_factory=list, description="提取的标签列表")
    issueCategories: List[IssueCategoryResult] = Field(default_factory=list, description="差评归因类别列表（V0.3 新增）")
    negativeReason: Optional[str] = Field(default=None, description="差评归因主类别（兼容旧版）")
    modelName: Optional[str] = Field(default=None)
    modelVersion: Optional[str] = Field(default=None)
    promptVersion: Optional[str] = Field(default="review-analysis:v1")
    businessTraceId: Optional[str] = Field(default=None, description="AI 调用追踪ID")
    status: str = Field(default="SUCCESS", description="PENDING/SUCCESS/FAILED")
    errorMessage: Optional[str] = Field(default=None, description="失败原因")


class BatchAnalyzeRequest(BaseModel):
    """批量分析请求"""
    reviews: List[AnalyzeRequest] = Field(..., max_length=100)
    modelVersion: Optional[str] = Field(default=None)
    systemPrompt: Optional[str] = Field(
        default=None,
        max_length=50000,
    )
    promptVersion: Optional[str] = Field(
        default=None,
        max_length=255,
    )
    analysisMode: Optional[str] = Field(
        default=None,
        description="覆盖全局 SENTIMENT_ANALYSIS_MODE: local / llm / hybrid。不传则使用服务端默认值",
    )


class BatchAnalyzeResponse(BaseModel):
    """批量分析响应"""
    successCount: int
    failCount: int
    results: List[AnalyzeResponse]
    errors: List[dict] = Field(default_factory=list)


class HealthResponse(BaseModel):
    """健康检查 — V0.3"""
    service: str = "ai-service"
    status: str = "UP"
    dependencies: dict = Field(default_factory=lambda: {
        "openSearch": "UNKNOWN",
        "modelApi": "UNKNOWN"
    })
    timestamp: str = Field(default="")

# ============================================
# 评价智能总结（EPIC-01 Story 7）
# ============================================
class SummaryReviewItem(BaseModel):
    """送入摘要生成的单条评价"""
    reviewId: int
    rating: int = Field(ge=1, le=5)
    content: str = Field(..., min_length=1)
    reviewTime: Optional[str] = Field(default=None, description="ISO 时间字符串")


class ReviewSummaryRequest(BaseModel):
    """摘要生成请求 — 由 Spring Boot 传入评论列表"""
    requestId: Optional[str] = None
    merchantId: int
    version: int = Field(default=1, ge=1)
    reviews: List[SummaryReviewItem] = Field(default_factory=list)
    minimumReviewCount: int = Field(default=3, ge=1)
    systemPrompt: Optional[str] = Field(
        default=None,
        max_length=50000,
        description="运行时系统提示词",
    )
    promptVersion: Optional[str] = Field(
        default=None,
        max_length=255,
        description="运行时提示词版本",
    )


class SummaryPoint(BaseModel):
    """摘要要点（优点/不足/推荐菜）"""
    name: str
    mentionCount: int = Field(default=0, ge=0)
    reviewIds: List[int] = Field(default_factory=list)


class SummaryEvidenceTypeEnum(str, Enum):
    ADVANTAGE = "ADVANTAGE"
    DISADVANTAGE = "DISADVANTAGE"
    DISH = "DISH"
    ENVIRONMENT = "ENVIRONMENT"
    SERVICE = "SERVICE"
    RECENT_CHANGE = "RECENT_CHANGE"


class SummaryEvidence(BaseModel):
    """摘要依据 — 关联原始评价"""
    reviewId: int
    evidenceType: SummaryEvidenceTypeEnum
    evidenceExcerpt: Optional[str] = None


class ReviewSummaryResponse(BaseModel):
    """摘要生成结果（扁平结构，与 AnalyzeResponse 风格一致）"""
    merchantId: int
    version: int = 1
    summaryStatus: str = Field(default="SUCCESS", description="SUCCESS/INSUFFICIENT_DATA/FAILED")
    summaryText: Optional[str] = None
    advantages: List[SummaryPoint] = Field(default_factory=list)
    disadvantages: List[SummaryPoint] = Field(default_factory=list)
    recommendedDishes: List[SummaryPoint] = Field(default_factory=list)
    environmentSummary: dict = Field(default_factory=dict)
    serviceSummary: dict = Field(default_factory=dict)
    recentChanges: List[dict] = Field(default_factory=list)
    reviewCount: int = 0
    minimumReviewCount: int = 3
    evidences: List[SummaryEvidence] = Field(default_factory=list)
    modelName: Optional[str] = None
    modelVersion: Optional[str] = None
    promptVersion: Optional[str] = "review-summary:v1"
    businessTraceId: Optional[str] = None
    errorMessage: Optional[str] = None


# ============================================
# 商家亮点挖掘（EPIC-02 Story 5）
# ============================================

class HighlightTypeEnum(str, Enum):
    """亮点类型枚举"""
    SIGNATURE_DISH = "SIGNATURE_DISH"    # 招牌菜
    ENVIRONMENT = "ENVIRONMENT"          # 环境特色
    SERVICE = "SERVICE"                  # 服务特点
    PRICE = "PRICE"                      # 价格优势
    BRAND_FEATURE = "BRAND_FEATURE"      # 品牌特色


class HighlightReviewItem(BaseModel):
    """送入亮点挖掘的单条正面评价"""
    reviewId: int
    rating: int = Field(ge=1, le=5)
    content: str = Field(..., min_length=1)
    reviewTime: Optional[str] = Field(default=None, description="ISO 时间字符串")
    # 已有的分析结果，辅助模型更准确挖掘
    keywords: List[str] = Field(default_factory=list)
    sentiment: Optional[str] = Field(default=None)


class HighlightGenerateRequest(BaseModel):
    """亮点生成请求 — 由 Spring Boot 传入正面评论列表"""
    requestId: Optional[str] = None
    merchantId: int
    version: int = Field(default=1, ge=1)
    reviews: List[HighlightReviewItem] = Field(default_factory=list)
    minimumPositiveCount: int = Field(default=5, ge=1)


class HighlightItem(BaseModel):
    """单条亮点"""
    highlightType: str = Field(description="SIGNATURE_DISH/ENVIRONMENT/SERVICE/PRICE/BRAND_FEATURE")
    title: str = Field(description="亮点标题，如'招牌拿铁广受好评'")
    description: str = Field(description="亮点详细描述")
    mentionCount: int = Field(default=1, ge=1)
    positiveRatio: float = Field(ge=0, le=1, description="好评占比0~1")
    reviewIds: List[int] = Field(default_factory=list, description="支撑该亮点的评价ID列表")


class HighlightEvidence(BaseModel):
    """亮点依据"""
    reviewId: int
    highlightType: str
    evidenceExcerpt: Optional[str] = None


class HighlightGenerateResponse(BaseModel):
    """亮点生成结果"""
    merchantId: int
    version: int = 1
    highlightStatus: str = Field(default="SUCCESS", description="SUCCESS/INSUFFICIENT_DATA/FAILED")
    highlights: List[HighlightItem] = Field(default_factory=list)
    reviewCount: int = 0
    minimumPositiveCount: int = 5
    evidences: List[HighlightEvidence] = Field(default_factory=list)
    modelName: Optional[str] = None
    modelVersion: Optional[str] = None
    promptVersion: Optional[str] = "merchant-highlight:v1"
    businessTraceId: Optional[str] = None
    errorMessage: Optional[str] = None


# ---- 评价辅助回复（EPIC-02 故事7） ----

class ReplyStrategyEnum(str, Enum):
    """AI 回复策略枚举"""
    POSITIVE = "POSITIVE"  # 好评策略：感谢 + 回应具体优点
    NEGATIVE = "NEGATIVE"  # 差评策略：道歉 + 问题说明 + 改进承诺


class GenerateReplyRequest(BaseModel):
    """AI 生成回复建议的请求 — 由 Spring Boot 传入评价信息"""
    reviewId: int
    merchantId: int
    content: str = Field(..., min_length=1, description="评价正文内容")
    strategy: ReplyStrategyEnum = Field(..., description="回复策略：POSITIVE 或 NEGATIVE")
    rating: int = Field(default=3, ge=1, le=5, description="评价评分（1-5）")
    systemPrompt: Optional[str] = Field(
        default=None,
        max_length=50000,
        description="运行时系统提示词",
    )
    promptVersion: Optional[str] = Field(
        default=None,
        max_length=255,
        description="运行时提示词版本",
    )


class GenerateReplyResponse(BaseModel):
    """AI 生成回复建议的响应"""
    reviewId: int
    replyContent: str = Field(..., description="AI 生成的回复建议内容")
    strategy: ReplyStrategyEnum = Field(..., description="使用的回复策略")
    modelName: Optional[str] = Field(default=None, description="使用的模型名称")
    modelVersion: Optional[str] = None
    promptVersion: Optional[str] = None
    businessTraceId: Optional[str] = Field(default=None, description="调用追踪 ID")
    status: str = Field(default="SUCCESS", description="SUCCESS / FAILED")
    errorMessage: Optional[str] = Field(default=None, description="失败时的错误信息")


# ============================================
# 周边竞品对比（EPIC-02 Story 6）
# ============================================

class CompetitorMerchantData(BaseModel):
    """
    单个竞品商家的统计数据，由 Spring Boot 从数据库查询后传入。
    每个字段都是真实统计数据，AI 只做文字总结，不编造数据。
    """
    merchantId: int = Field(description="商家ID")
    merchantName: str = Field(description="商家名称")
    category: str = Field(description="商家类别，如 火锅、川菜、咖啡厅")
    cuisine: Optional[str] = Field(default=None, description="菜系")
    address: Optional[str] = Field(default=None, description="地址")
    averagePrice: Optional[float] = Field(default=None, description="人均消费金额")
    rating: Optional[float] = Field(default=None, ge=0, le=5, description="综合评分（0~5）")
    reviewCount: int = Field(default=0, ge=0, description="评价总数")
    positiveRate: Optional[float] = Field(default=None, ge=0, le=1, description="好评率（0~1）")
    tasteRating: Optional[float] = Field(default=None, ge=0, le=5, description="口味评分均值")
    environmentRating: Optional[float] = Field(default=None, ge=0, le=5, description="环境评分均值")
    serviceRating: Optional[float] = Field(default=None, ge=0, le=5, description="服务评分均值")
    topPositiveTags: List[str] = Field(default_factory=list, description="高频正面标签（Top-5）")
    topNegativeIssues: List[str] = Field(default_factory=list, description="主要差评问题（Top-5）")


class CompetitorComparisonRequest(BaseModel):
    """
    竞品对比请求 — 由 Spring Boot 传入本店与竞品的统计数据。
    本店为列表中第一个（isSelf=True），竞品为其余。
    """
    requestId: Optional[str] = Field(default=None, description="请求追踪ID")
    merchantId: int = Field(description="发起对比的商家ID（本店）")
    competitors: List[CompetitorMerchantData] = Field(
        ..., min_length=2, max_length=4,
        description="包含本店在内的商家数据列表，第一个必须是本店，总数为2~4家"
    )
    systemPrompt: Optional[str] = Field(
        default=None,
        max_length=50000,
        description="运行时系统提示词",
    )
    promptVersion: Optional[str] = Field(
        default=None,
        max_length=255,
        description="运行时提示词版本",
    )


class CompetitorSingleComparisonResult(BaseModel):
    """单家商家的对比结果（AI 生成的文字分析）"""
    merchantId: int
    merchantName: str
    strengths: List[str] = Field(default_factory=list, description="相对于竞品的优势")
    weaknesses: List[str] = Field(default_factory=list, description="相对于竞品的短板")
    overallAssessment: str = Field(default="", description="综合评价（1~2句）")


class CompetitorComparisonResponse(BaseModel):
    """
    竞品对比响应 — AI 生成对比分析文字。
    前端配合 Spring Boot 提供的统计数据渲染图表。
    """
    merchantId: int = Field(description="本店ID")
    comparisonStatus: str = Field(default="SUCCESS", description="SUCCESS / FAILED")
    # AI 对每家商家的分析（包含本店）
    merchantAnalyses: List[CompetitorSingleComparisonResult] = Field(default_factory=list)
    # 横向对比总结（2~3 句话概括本店在竞品中的定位）
    summaryText: Optional[str] = Field(default=None, description="横向对比总结")
    # 给本店的改进建议
    improvementSuggestions: List[str] = Field(default_factory=list, description="基于对比差距的改进建议")
    modelName: Optional[str] = Field(default=None)
    modelVersion: Optional[str] = None
    promptVersion: Optional[str] = Field(default="competitor-comparison:v1")
    businessTraceId: Optional[str] = Field(default=None, description="AI 调用追踪ID")
    errorMessage: Optional[str] = Field(default=None)


# ============================================
# 评论摘要忠实性测试（EPIC-01 Story 8）
# 核心功能：用 LLM-as-Judge 验证摘要中的每个要点是否忠实于原始评价
# 验证逻辑：对于摘要中的每个声明（要点），将声明文本 + 支撑评价原文
#           送入独立的评判模型，判断声明是否被评价内容所蕴含（Entailment）
# ============================================

class FaithfulnessVerdictEnum(str, Enum):
    """
    忠实性判定结果枚举

    - FAITHFUL:    声明可以被引用的评价原文充分支撑，没有编造或歪曲
    - UNFAITHFUL:  声明与引用评价原文矛盾，或包含评价中不存在的信息
    - UNCERTAIN:   引用评价部分支撑声明，但不足以完全确认（如信息模糊）
    """
    FAITHFUL = "FAITHFUL"
    UNFAITHFUL = "UNFAITHFUL"
    UNCERTAIN = "UNCERTAIN"


class FaithfulnessReviewItem(BaseModel):
    """
    送入忠实性测试的单条评价原文

    与 SummaryReviewItem 类似但去掉了 rating 的强制要求，
    因为忠实性测试只需要内容 + ID 即可完成回溯验证
    """
    reviewId: int = Field(description="评价ID，用于与摘要中的 reviewIds 对应")
    content: str = Field(..., min_length=1, description="评价原文内容")
    rating: Optional[int] = Field(default=None, ge=1, le=5, description="评分（可选，辅助判断）")


class FaithfulnessTestRequest(BaseModel):
    """
    评论摘要忠实性测试请求

    由 Spring Boot 传入已生成的摘要结果和原始评价原文列表，
    AI 服务对每个摘要要点逐一做忠实性验证

    设计要点：
    - summary: 已经生成的 ReviewSummaryResponse（商家口碑摘要）
    - reviews: 参与生成该摘要的原始评价列表（带原文内容）
    - 服务端根据 summary 中的 reviewIds 自动匹配 reviews 中的原文作为证据
    """
    requestId: Optional[str] = Field(default=None, description="请求追踪ID")
    merchantId: int = Field(description="商家ID，用于日志追溯")
    summary: ReviewSummaryResponse = Field(description="已生成的商家口碑摘要结果")
    reviews: List[FaithfulnessReviewItem] = Field(
        default_factory=list,
        min_length=1,
        description="参与摘要生成的原始评价列表（必须包含原文内容）"
    )


class FaithfulnessClaimResult(BaseModel):
    """
    单个声明（要点）的忠实性验证结果

    每个字段都是在回答"这个声明是否忠实地反映了评价原文"这一问题
    """
    claimType: str = Field(
        description="声明类型：advantage / disadvantage / recommendedDish / "
                    "environmentSummary / serviceSummary / recentChange / summaryText"
    )
    claimText: str = Field(description="声明的文本内容，如'菜品口味好'")
    verdict: FaithfulnessVerdictEnum = Field(
        description="忠实性判定：FAITHFUL / UNFAITHFUL / UNCERTAIN"
    )
    confidence: float = Field(
        ge=0, le=1,
        description="判定置信度（0~1），由评判模型给出"
    )
    reasoning: str = Field(
        description="判定理由：为什么该声明是忠实的/不忠实的/不确定的，"
                    "需要引用评价原文中的具体语句作为论据"
    )
    citedReviewIds: List[int] = Field(
        default_factory=list,
        description="摘要中引用的支撑评价ID列表"
    )
    actualMatchingCount: int = Field(
        default=0,
        description="实际匹配到的有效评价数量（reviewIds 中能在输入中找到原文的个数）"
    )


class FaithfulnessTestResponse(BaseModel):
    """
    评论摘要忠实性测试响应

    返回摘要中每个声明的忠实性判定及整体忠实性评分

    各字段含义：
    - overallScore: 整体忠实性得分（FAITHFUL 占比），0~1
    - claimResults: 每个声明的详细验证结果
    - totalClaims: 被测试的声明总数
    - faithfulCount / unfaithfulCount / uncertainCount: 各类判定计数
    - 附带摘要文本用于前端直接渲染结果对照
    """
    merchantId: int = Field(description="商家ID")
    testStatus: str = Field(
        default="SUCCESS",
        description="测试执行状态：SUCCESS / PARTIAL（部分声明验证失败）/ FAILED"
    )
    overallScore: float = Field(
        ge=0, le=1,
        description="整体忠实性得分，即 FAITHFUL 声明占总声明数的比例"
    )
    totalClaims: int = Field(default=0, description="被验证的声明总数")
    faithfulCount: int = Field(default=0, description="忠实声明的数量")
    unfaithfulCount: int = Field(default=0, description="不忠实声明的数量")
    uncertainCount: int = Field(default=0, description="不确定声明的数量")
    claimResults: List[FaithfulnessClaimResult] = Field(
        default_factory=list,
        description="每个声明的详细忠实性验证结果"
    )
    summaryText: Optional[str] = Field(
        default=None,
        description="原始摘要的总体口碑概述文本（供前端对照展示）"
    )
    modelName: Optional[str] = Field(
        default=None,
        description="用于忠实性评判的模型名称"
    )
    modelVersion: Optional[str] = Field(default=None)
    promptVersion: Optional[str] = Field(default="faithfulness-test:v1")
    businessTraceId: Optional[str] = Field(
        default=None,
        description="AI 调用追踪ID，用于排查问题"
    )
    errorMessage: Optional[str] = Field(default=None, description="测试过程中的错误信息")


# ============================================
# 经营改进建议生成（EPIC-02 Story 8）
# ============================================

class BusinessSuggestionDataSource(BaseModel):
    """数据源状态"""
    sourceType: str = Field(description="数据源类型：REPUTATION_TREND / NEGATIVE_ISSUE / HIGHLIGHT / COMPETITOR")
    available: bool = Field(default=True)
    dataCount: int = Field(default=0)
    minimumRequired: int = Field(default=1)


class BusinessSuggestionRequest(BaseModel):
    """
    经营改进建议生成请求

    由 Spring Boot 聚合口碑趋势、差评归因、商家亮点和竞品对比数据后传入。
    AI 基于这些数据生成结构化改进建议。
    """
    merchantId: int = Field(ge=1, description="商家ID")
    version: int = Field(default=1, ge=1, description="建议版本号")
    reviewCount: int = Field(default=0, ge=0, description="有效评价总数")
    minimumReviewCount: int = Field(default=5, ge=1, description="最少需要评价数")

    # 口碑趋势数据（可选）
    reputationTrends: Optional[List[dict]] = Field(
        default=None, description="口碑趋势统计点列表"
    )

    # 差评归因统计（可选）
    issueStats: Optional[List[dict]] = Field(
        default=None, description="差评类别统计列表"
    )

    # 商家亮点（可选）
    highlights: Optional[List[dict]] = Field(
        default=None, description="已有商家亮点列表"
    )

    # 竞品数据（可选）
    competitors: Optional[List[dict]] = Field(
        default=None, description="周边竞品基础数据"
    )


class BusinessSuggestionItem(BaseModel):
    """单条经营改进建议"""
    title: str = Field(..., min_length=1, max_length=500,
                       description="建议标题，如'优化周末高峰期出餐速度'")
    description: str = Field(..., min_length=1,
                             description="建议详细描述，含问题分析和具体改进措施")
    category: str = Field(
        description="REPUTATION_TREND / NEGATIVE_ISSUE / HIGHLIGHT_GAP / COMPETITOR_GAP"
    )
    priority: str = Field(default="MEDIUM", description="HIGH / MEDIUM / LOW")
    timeframe: str = Field(default="SHORT_TERM", description="SHORT_TERM / LONG_TERM")
    expectedEffect: Optional[str] = Field(default=None, description="预期改进效果描述")
    dataBasisType: Optional[str] = Field(default=None,
                                         description="数据依据类型")
    dataBasisSummary: Optional[str] = Field(default=None, description="数据依据摘要")
    metricName: Optional[str] = Field(default=None, description="相关指标名称")
    metricValue: Optional[str] = Field(default=None, description="指标数值")
    confidence: str = Field(default="MEDIUM", description="HIGH / MEDIUM / LOW")

    # 依据关联
    evidences: Optional[List[dict]] = Field(
        default=None,
        description="建议依据列表，每项可含 sourceType/sourceId/reviewId/evidenceExcerpt/metricSnapshot"
    )


class BusinessSuggestionResponse(BaseModel):
    """
    经营改进建议生成响应
    """
    merchantId: int = Field(description="商家ID")
    version: int = Field(description="建议版本号")
    status: str = Field(default="SUCCESS",
                        description="SUCCESS / FAILED / INSUFFICIENT_DATA")
    suggestions: List[BusinessSuggestionItem] = Field(
        default_factory=list, description="生成的改进建议列表，最多 10 条"
    )
    summaryText: Optional[str] = Field(
        default=None, description="总体经营状况概述（2~3句话）"
    )
    dataSufficiency: Optional[str] = Field(
        default=None, description="数据充足性评估：SUFFICIENT / INSUFFICIENT"
    )
    modelName: Optional[str] = Field(default=None)
    modelVersion: Optional[str] = None
    promptVersion: Optional[str] = Field(default="business-suggestion:v1")
    businessTraceId: Optional[str] = Field(default=None, description="AI 调用追踪ID")
    errorMessage: Optional[str] = Field(default=None)
