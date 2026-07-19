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
    businessTraceId: Optional[str] = Field(default=None, description="AI 调用追踪ID")
    status: str = Field(default="SUCCESS", description="PENDING/SUCCESS/FAILED")
    errorMessage: Optional[str] = Field(default=None, description="失败原因")


class BatchAnalyzeRequest(BaseModel):
    """批量分析请求"""
    reviews: List[AnalyzeRequest] = Field(..., max_length=100)
    modelVersion: Optional[str] = Field(default=None)


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


class GenerateReplyResponse(BaseModel):
    """AI 生成回复建议的响应"""
    reviewId: int
    replyContent: str = Field(..., description="AI 生成的回复建议内容")
    strategy: ReplyStrategyEnum = Field(..., description="使用的回复策略")
    modelName: Optional[str] = Field(default=None, description="使用的模型名称")
    businessTraceId: Optional[str] = Field(default=None, description="调用追踪 ID")
    status: str = Field(default="SUCCESS", description="SUCCESS / FAILED")
    errorMessage: Optional[str] = Field(default=None, description="失败时的错误信息")
