"""
Pydantic 数据模型 — V0.2，与接口规范/数据库设计对齐
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


class AnalyzeRequest(BaseModel):
    """单条评价分析请求"""
    reviewId: int
    merchantId: int
    content: str = Field(..., min_length=1, description="评价原文内容")
    modelVersion: Optional[str] = Field(default=None)


class AnalyzeResponse(BaseModel):
    """单条评价分析响应 — V0.2"""
    reviewId: int
    merchantId: int
    sentiment: str = Field(description="POSITIVE/NEUTRAL/NEGATIVE/MIXED")
    confidence: float = Field(ge=0, le=1, description="整体置信度")
    lowConfidence: bool = Field(default=False, description="是否低于置信度阈值")
    keywords: List[str] = Field(default_factory=list, description="关键词列表")
    aspects: List[AspectResult] = Field(default_factory=list, description="方面级情感")
    tags: List[TagResult] = Field(default_factory=list, description="提取的标签列表")
    negativeReason: Optional[str] = Field(default=None, description="差评归因类别")
    modelName: Optional[str] = Field(default=None)
    modelVersion: Optional[str] = Field(default=None)
    status: str = Field(default="SUCCESS", description="PENDING/SUCCESS/FAILED")


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
    """健康检查 — V0.2"""
    service: str = "ai-service"
    status: str = "UP"
    dependencies: dict = Field(default_factory=lambda: {
        "openSearch": "UNKNOWN",
        "modelApi": "UNKNOWN"
    })
    timestamp: str = Field(default="")
