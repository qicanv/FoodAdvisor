"""
语义检索相关 schema — POST /internal/search/semantic

查询 → embedding → OpenSearch k-NN 检索 → 返回相关文档
"""
from typing import Optional
from pydantic import BaseModel, Field


class SearchFilter(BaseModel):
    """检索过滤条件（所有条件为 AND 关系）"""
    merchantIds: Optional[list[int]] = Field(
        default=None, description="限定商家 ID 列表"
    )
    sourceTypes: Optional[list[str]] = Field(
        default=None, description="限定来源类型：MERCHANT_INTRO / MENU / REVIEW"
    )


class SearchRequest(BaseModel):
    """语义检索请求"""
    requestId: Optional[str] = Field(default=None, description="请求追踪 ID")
    query: str = Field(..., min_length=1, max_length=2000, description="用户查询文本")
    topK: int = Field(default=5, ge=1, le=100, description="返回结果数量")
    filters: Optional[SearchFilter] = Field(default=None, description="检索过滤条件")


class SearchResultItem(BaseModel):
    """单条检索结果"""
    documentId: str = Field(description="文档标识（如 review-101）")
    chunkId: str = Field(description="Chunk 唯一标识")
    merchantId: int = Field(description="商家编号")
    sourceType: str = Field(description="来源类型")
    sourceId: int = Field(description="原始记录编号")
    text: str = Field(description="匹配文本内容")
    score: float = Field(description="相似度分数（0~1）")
    updatedAt: Optional[str] = Field(default=None, description="更新时间（ISO 格式）")


class SearchData(BaseModel):
    """检索结果数据体"""
    searchMode: str = Field(
        default="VECTOR", description="检索模式：VECTOR / KEYWORD_FALLBACK"
    )
    results: list[SearchResultItem] = Field(default_factory=list)


class SearchResponse(BaseModel):
    """语义检索响应"""
    requestId: Optional[str] = None
    status: str = Field(default="SUCCESS", description="SUCCESS / FAILED")
    data: SearchData = Field(default_factory=SearchData)
