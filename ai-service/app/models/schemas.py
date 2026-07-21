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
    minimumReviewCount: int = Field(default=5, ge=1)
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
    minimumReviewCount: int = 5
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
