from enum import Enum
from math import isfinite
from typing import Any, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, SecretStr, field_validator, model_validator
from app.schemas.constraint_patch import ConstraintPatch


ALLOWED_FIELDS = {
    "partySize",
    "totalBudget",
    "perCapitaBudget",
    "merchantTypes",
    "cuisines",
    "tastePreferences",
    "tasteRestrictions",
    "dishKeywords",
    "excludedCuisines",
    "excludedMerchantTypes",
    "distanceKm",
    "minRating",
    "ratingPreference",
    "scenes",
    "environmentRequirements",
    "businessTime",
    "businessTargetTime",
    "businessTargetNextDay",
    "businessTargetDate",
    "businessTargetDayOfWeek",
    "businessTimeWindow",
    "timezone",
}


class DialogueIntent(str, Enum):
    MERCHANT_RECOMMENDATION = "MERCHANT_RECOMMENDATION"
    CONSTRAINT_UPDATE = "CONSTRAINT_UPDATE"
    GENERAL_CHAT = "GENERAL_CHAT"
    UNKNOWN = "UNKNOWN"


class ConstraintStateModel(BaseModel):
    model_config = ConfigDict(extra="forbid")

    partySize: Optional[int] = Field(default=None, ge=1, le=20)
    totalBudget: Optional[float] = Field(default=None, gt=0)
    perCapitaBudget: Optional[float] = Field(default=None, gt=0)
    merchantTypes: list[str] = Field(default_factory=list)
    cuisines: list[str] = Field(default_factory=list)
    tastePreferences: list[str] = Field(default_factory=list)
    tasteRestrictions: list[str] = Field(default_factory=list)
    dishKeywords: list[str] = Field(default_factory=list)
    excludedCuisines: list[str] = Field(default_factory=list)
    excludedMerchantTypes: list[str] = Field(default_factory=list)
    distanceKm: Optional[float] = Field(default=None, gt=0, le=100)
    minRating: Optional[float] = Field(default=None, ge=0, le=5)
    ratingPreference: Optional[Literal["HIGH"]] = None
    scenes: list[str] = Field(default_factory=list)
    environmentRequirements: list[str] = Field(default_factory=list)
    businessTime: Optional[str] = None
    businessTargetTime: Optional[str] = Field(
        default=None,
        pattern=r"^(?:[01]\d|2[0-3]):[0-5]\d$",
    )
    businessTargetNextDay: Optional[bool] = None
    businessTargetDate: Optional[str] = Field(
        default=None,
        pattern=r"^\d{4}-\d{2}-\d{2}$",
    )
    businessTargetDayOfWeek: Optional[int] = Field(
        default=None,
        ge=1,
        le=7,
    )
    businessTimeWindow: Optional[str] = None
    timezone: Optional[str] = Field(default="Asia/Shanghai", max_length=100)

    constraintStrengths: dict[str, Any] = Field(
        default_factory=dict,
        exclude=True,
    )

    @field_validator("totalBudget", "perCapitaBudget", "distanceKm", "minRating")
    @classmethod
    def finite_number(cls, value: Optional[float]) -> Optional[float]:
        if value is not None and not isfinite(value):
            raise ValueError("number must be finite")
        return value

    @field_validator(
        "merchantTypes",
        "cuisines",
        "tastePreferences",
        "tasteRestrictions",
        "dishKeywords",
        "excludedCuisines",
        "excludedMerchantTypes",
        "scenes",
        "environmentRequirements",
        mode="before",
    )
    @classmethod
    def clean_string_list(cls, value: Any) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            raise ValueError("value must be a string list")

        result: list[str] = []
        seen: set[str] = set()
        for item in value:
            if not isinstance(item, str):
                raise ValueError("list items must be strings")
            trimmed = item.strip()
            if not trimmed:
                continue
            if len(trimmed) > 30:
                raise ValueError("list item too long")
            if trimmed not in seen:
                seen.add(trimmed)
                result.append(trimmed)
        return result[:10]

    @field_validator("businessTime")
    @classmethod
    def valid_business_time(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        if value not in {"NOW_OPEN", "TONIGHT", "LATE_NIGHT"}:
            raise ValueError("invalid businessTime")
        return value


class RuntimeModelConfigModel(BaseModel):
    """Spring Boot 为本次 AI 调用提供的运行时模型配置。"""

    model_config = ConfigDict(extra="forbid")

    provider: str = Field(
        min_length=1,
        max_length=100,
    )
    modelName: str = Field(
        min_length=1,
        max_length=255,
    )
    baseUrl: str = Field(
        min_length=1,
        max_length=2048,
    )
    apiKey: SecretStr = Field(
        min_length=1,
        max_length=4096,
    )
    timeoutMs: int = Field(
        gt=0,
        le=300000,
    )
    temperature: float = Field(
        ge=0,
        le=2,
    )
    maxOutputTokens: int = Field(
        gt=0,
        le=100000,
    )

    @field_validator(
        "provider",
        "modelName",
        "baseUrl",
    )
    @classmethod
    def required_text_not_blank(
        cls,
        value: str,
    ) -> str:
        trimmed = value.strip()

        if not trimmed:
            raise ValueError(
                "runtime model text field must not be blank"
            )

        return trimmed

    @field_validator("baseUrl")
    @classmethod
    def base_url_must_use_http(
        cls,
        value: str,
    ) -> str:
        if not value.startswith(
            ("http://", "https://")
        ):
            raise ValueError(
                "baseUrl must use http or https"
            )

        return value.rstrip("/")

class DialogueExtractRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sessionId: int = Field(gt=0)
    messageId: int = Field(gt=0)
    content: str
    currentConstraints: ConstraintStateModel = Field(
        default_factory=ConstraintStateModel
    )
    recentMessages: list[dict[str, str]] = Field(default_factory=list, max_length=6)
    rejectedFields: list[str] = Field(default_factory=list)
    pendingConflicts: list[dict] = Field(default_factory=list)
    timezone: str = Field(default="Asia/Shanghai", max_length=100)
    runtimeModel: RuntimeModelConfigModel
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

    @field_validator("content")
    @classmethod
    def content_not_blank(cls, value: str) -> str:
        if value is None or not value.strip():
            raise ValueError("content must not be blank")
        return value.strip()


class DialogueExtractResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    intent: DialogueIntent
    extractedConstraints: ConstraintStateModel = Field(
        default_factory=ConstraintStateModel
    )
    clearedFields: list[str] = Field(default_factory=list)
    confidence: float = Field(default=0.0, ge=0, le=1)
    extractor: str = "AI_MODEL"
    degraded: bool = False
    modelName: Optional[str] = None
    modelVersion: Optional[str] = None
    promptVersion: Optional[str] = "dialogue-extraction:v1"
    provider: Optional[str] = None
    patch: Optional[ConstraintPatch] = None

    @field_validator("clearedFields", mode="before")
    @classmethod
    def clean_cleared_fields(cls, value: Any) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            raise ValueError("clearedFields must be a list")

        result: list[str] = []
        seen: set[str] = set()
        for item in value:
            if not isinstance(item, str):
                raise ValueError("clearedFields items must be strings")
            trimmed = item.strip()
            if trimmed not in ALLOWED_FIELDS:
                raise ValueError(f"unsupported cleared field: {trimmed}")
            if trimmed not in seen:
                seen.add(trimmed)
                result.append(trimmed)
        return result

    @model_validator(mode="before")
    @classmethod
    def reject_forbidden_fields(cls, values: Any) -> Any:
        serialized = str(values)
        for forbidden in ("merchantId", "merchantName", "latitude", "longitude"):
            if forbidden in serialized:
                raise ValueError(f"forbidden field: {forbidden}")
        return values
