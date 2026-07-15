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
