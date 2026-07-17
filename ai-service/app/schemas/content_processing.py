"""
内容清洗与切分相关的 Pydantic schema 定义

支持：
- 商家介绍（MERCHANT_INTRO）、菜单（MENU）、用户评价（REVIEW）三种来源
- 清洗配置：HTML 标签、控制字符、重复空格、无意义内容、日期/价格标准化
- 切分配置：最大块长度、上下文重叠、句子边界保持
- 每个文本块保留 merchantId、sourceType、sourceId、时间等来源信息
"""
from enum import Enum
from typing import Optional, List
from pydantic import BaseModel, Field, field_validator


class SourceTypeEnum(str, Enum):
    MERCHANT_INTRO = "MERCHANT_INTRO"   # 商家介绍
    MENU = "MENU"                       # 菜单描述
    REVIEW = "REVIEW"                   # 用户评价


class CleanConfig(BaseModel):
    """清洗配置"""
    removeHtmlTags: bool = Field(default=True, description="去除 HTML 标签及实体")
    removeControlChars: bool = Field(default=True, description="去除控制字符")
    collapseSpaces: bool = Field(default=True, description="合并重复空格和换行")
    removeMeaninglessPatterns: bool = Field(
        default=True, description='去除无意义内容（如"该用户没有填写评价"）'
    )
    normalizeDates: bool = Field(default=True, description="统一日期格式为 YYYY-MM-DD")
    normalizePrices: bool = Field(default=True, description="统一价格格式为 ¥XX")
    customReplacements: dict[str, str] = Field(
        default_factory=dict, description="自定义替换规则 {old: new}"
    )


class ChunkConfig(BaseModel):
    """切分配置"""
    maxChunkLength: int = Field(
        default=512, ge=64, le=8192, description="最大块长度（字符数）"
    )
    overlapLength: int = Field(
        default=64, ge=0, le=512, description="上下文重叠长度（字符数）"
    )
    separator: str = Field(default="\n", description="切分优先分隔符")
    preserveSentenceBoundary: bool = Field(
        default=True, description="优先在句子边界处切分"
    )

    @field_validator("overlapLength")
    @classmethod
    def overlap_lt_max(cls, v, info):
        if "maxChunkLength" in info.data and v >= info.data["maxChunkLength"]:
            raise ValueError("overlapLength must be less than maxChunkLength")
        return v


class ContentChunk(BaseModel):
    """单个文本块 — 清洗+切分后的结果单元"""
    chunkId: str = Field(description="Chunk 唯一标识（SHA256 确定性生成）")
    merchantId: int = Field(description="商家编号")
    sourceType: SourceTypeEnum = Field(description="内容来源类型")
    sourceId: int = Field(description="原始记录编号（reviewId / dishId / merchantId）")
    sourceTimestamp: Optional[str] = Field(
        default=None, description="原始内容时间（ISO 格式）"
    )
    chunkIndex: int = Field(ge=0, description="块序号（从 0 开始）")
    totalChunks: int = Field(ge=1, description="该来源的总块数")
    rawText: str = Field(description="原始文本（清洗前）")
    cleanedText: str = Field(description="清洗后文本")
    metadata: dict = Field(default_factory=dict, description="附加元数据")


class ContentItem(BaseModel):
    """单条待处理内容"""
    merchantId: int = Field(gt=0)
    sourceType: SourceTypeEnum
    sourceId: int = Field(gt=0)
    sourceTimestamp: Optional[str] = Field(default=None)
    content: str = Field(..., min_length=1, description="待处理文本")
    metadata: dict = Field(default_factory=dict)

    @field_validator("content")
    @classmethod
    def content_not_blank(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("content must not be blank")
        return v


class ProcessRequest(BaseModel):
    """清洗+切分批量请求"""
    requestId: Optional[str] = Field(default=None, description="请求追踪 ID")
    items: List[ContentItem] = Field(..., min_length=1, max_length=500)
    cleanConfig: CleanConfig = Field(default_factory=CleanConfig)
    chunkConfig: ChunkConfig = Field(default_factory=ChunkConfig)


class ProcessError(BaseModel):
    """单条处理失败记录"""
    merchantId: int
    sourceType: str
    sourceId: int
    error: str = Field(max_length=500)


class ProcessResult(BaseModel):
    """清洗+切分批量结果"""
    requestId: Optional[str] = None
    totalItems: int
    successCount: int
    failCount: int
    totalChunks: int
    chunks: List[ContentChunk] = Field(default_factory=list)
    errors: List[ProcessError] = Field(default_factory=list)


class QueryRequest(BaseModel):
    """查询/导出历史处理结果"""
    merchantId: Optional[int] = Field(default=None, gt=0)
    sourceType: Optional[SourceTypeEnum] = None
    sourceId: Optional[int] = Field(default=None, gt=0)
    includeRaw: bool = True
    includeCleaned: bool = True
    includeChunks: bool = True
