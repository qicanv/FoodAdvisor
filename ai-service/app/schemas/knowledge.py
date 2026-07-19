"""
知识向量化 Upsert 相关 schema

POST /internal/knowledge/upsert 的请求/响应模型。
接收 content/process 输出的 ContentChunk + 元数据，
调用 embedding 模型生成向量后写入 OpenSearch。
"""
from typing import Optional
from pydantic import BaseModel, Field, field_validator


# 合法的 sourceType（upsert 只接受具体来源，deactivate 还接受 MERCHANT）
_VALID_SOURCE_TYPES = {"MERCHANT_INTRO", "MENU", "REVIEW"}
_VALID_DEACTIVATE_SOURCE_TYPES = {"MERCHANT_INTRO", "MENU", "REVIEW", "MERCHANT"}


class KnowledgeDocument(BaseModel):
    """单条待写入知识文档 — 来自 content/process 的清洗后文本 + 元数据"""

    chunkId: str = Field(..., min_length=1, description="Chunk 唯一标识（确定性生成）")
    merchantId: int = Field(gt=0, description="商家编号")
    sourceType: str = Field(
        ..., min_length=1, description="来源类型：MERCHANT_INTRO / MENU / REVIEW"
    )
    sourceId: int = Field(gt=0, description="原始记录编号")
    contentVersion: int = Field(default=1, ge=1, description="来源内容版本号")
    chunkIndex: int = Field(default=0, ge=0, description="块序号（从 0 开始）")
    totalChunks: int = Field(default=1, ge=1, description="该来源的总块数")
    text: str = Field(..., min_length=1, description="清洗后文本（待 embedding）")
    sourceTimestamp: Optional[str] = Field(
        default=None, description="原始内容时间（ISO 格式）"
    )

    @field_validator("sourceType")
    @classmethod
    def validate_source_type(cls, v: str) -> str:
        if v not in _VALID_SOURCE_TYPES:
            raise ValueError(f"sourceType must be one of {_VALID_SOURCE_TYPES}, got: {v}")
        return v


class KnowledgeUpsertRequest(BaseModel):
    """批量知识文档写入请求"""

    requestId: Optional[str] = Field(default=None, description="请求追踪 ID")
    documents: list[KnowledgeDocument] = Field(
        ..., min_length=1, max_length=500, description="待写入文档列表"
    )


class KnowledgeDocumentResult(BaseModel):
    """单条写入结果"""

    chunkId: str
    documentId: str = Field(description="由 sourceType + sourceId 拼成的文档标识")
    status: str = Field(description="SUCCESS / SKIPPED / FAILED")
    error: Optional[str] = Field(default=None, description="失败原因（最长 500 字符）")

    @field_validator("status")
    @classmethod
    def validate_status(cls, v: str) -> str:
        allowed = {"SUCCESS", "SKIPPED", "FAILED"}
        if v not in allowed:
            raise ValueError(f"status must be one of {allowed}, got: {v}")
        return v


class KnowledgeUpsertResponse(BaseModel):
    """批量写入结果"""

    requestId: Optional[str] = None
    total: int = Field(ge=0, description="请求文档总数")
    successCount: int = Field(default=0, ge=0)
    skipCount: int = Field(default=0, ge=0, description="内容未变化跳过的数量")
    failCount: int = Field(default=0, ge=0)
    results: list[KnowledgeDocumentResult] = Field(default_factory=list)


# ============================================
# Deactivate — 停用知识文档
# ============================================


class KnowledgeDeactivateRequest(BaseModel):
    """批量停用知识文档请求

    sourceType 为 MERCHANT 时，按 merchantId 停用该商家下的全部文档；
    为 MERCHANT_INTRO / MENU / REVIEW 时，按 sourceType + sourceId 精确停用。
    """

    requestId: Optional[str] = Field(default=None, description="请求追踪 ID")
    sourceType: str = Field(
        ..., min_length=1,
        description="来源类型：MERCHANT（按商家停用全部）/ MERCHANT_INTRO / MENU / REVIEW"
    )
    sourceIds: list[int] = Field(
        ..., min_length=1, max_length=500,
        description="要停用的来源 ID 列表"
    )

    @field_validator("sourceType")
    @classmethod
    def validate_source_type(cls, v: str) -> str:
        if v not in _VALID_DEACTIVATE_SOURCE_TYPES:
            raise ValueError(
                f"sourceType must be one of {_VALID_DEACTIVATE_SOURCE_TYPES}, got: {v}"
            )
        return v


class KnowledgeDeactivateResponse(BaseModel):
    """批量停用结果"""

    requestId: Optional[str] = None
    sourceType: str
    sourceIds: list[int]
    deactivatedCount: int = Field(default=0, ge=0, description="实际停用的文档数")
    error: Optional[str] = Field(default=None, description="失败原因（成功时为空）")
