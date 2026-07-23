"""
违规文本检测相关的 Pydantic schema 定义

支持检测类型：
- AD_SPAM：广告引流（加微信、QQ、链接推广等）
- ABUSE：恶意谩骂（人身攻击、侮辱性言论）
- FALSE_AD：虚假宣传（夸大功效、虚假承诺）
- SPAM：无关灌水（无意义内容、重复刷屏）
- OTHER：其他违反平台规则的内容

设计原则：
- 确定性输出：相同 content + ruleVersion → 一致结果（LLM temperature=0）
- 结构化返回：风险类型、等级、分值、匹配规则、证据摘要
- 可追溯：modelName、businessTraceId 便于审计
"""
from enum import Enum
from typing import Optional, List
from pydantic import BaseModel, Field, field_validator


class RiskTypeEnum(str, Enum):
    """违规风险类型"""
    AD_SPAM = "AD_SPAM"         # 广告引流
    ABUSE = "ABUSE"             # 恶意谩骂
    FALSE_AD = "FALSE_AD"       # 虚假宣传
    SPAM = "SPAM"               # 无关灌水
    OTHER = "OTHER"             # 其他违规


class RiskLevelEnum(str, Enum):
    """风险等级"""
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class DetectionStatusEnum(str, Enum):
    """检测状态"""
    SUCCESS = "SUCCESS"
    FALLBACK = "FALLBACK"
    ERROR = "ERROR"
    TIMEOUT = "TIMEOUT"


class ViolationCheckRequest(BaseModel):
    """违规文本检测请求"""
    content: str = Field(
        ...,
        min_length=1,
        max_length=5000,
        description="待检测的文本内容"
    )
    ruleVersion: Optional[str] = Field(
        default=None,
        max_length=30,
        description="检测规则版本，用于追踪规则变更和确保确定性输出"
    )

    @field_validator("content")
    @classmethod
    def content_not_blank(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("content must not be blank")
        return v


class MatchedRule(BaseModel):
    """单条匹配的违规规则"""
    ruleCode: str = Field(description="规则编码，如 AD_SPAM_001")
    ruleName: str = Field(description="规则名称，如 包含联系方式推广")
    riskType: RiskTypeEnum = Field(description="风险类型")
    confidence: float = Field(
        ge=0.0,
        le=1.0,
        description="置信度 0~1"
    )
    evidenceExcerpt: Optional[str] = Field(
        default=None,
        description="触发规则的原文片段"
    )


class ViolationCheckResponse(BaseModel):
    """违规文本检测响应"""
    riskType: Optional[RiskTypeEnum] = Field(
        default=None,
        description="主要风险类型，无风险时为 None"
    )
    riskLevel: RiskLevelEnum = Field(description="风险等级")
    riskScore: int = Field(
        ge=0,
        le=100,
        description="风险分值 0~100，0 表示完全正常"
    )
    matchedRules: List[MatchedRule] = Field(
        default_factory=list,
        description="命中的规则列表"
    )
    modelName: Optional[str] = Field(
        default=None,
        description="使用的模型名称"
    )
    businessTraceId: Optional[str] = Field(
        default=None,
        description="业务追踪ID"
    )
    detectionStatus: DetectionStatusEnum = Field(
        default=DetectionStatusEnum.SUCCESS,
        description="检测状态"
    )
    errorMessage: Optional[str] = Field(
        default=None,
        description="检测失败时的错误信息"
    )
