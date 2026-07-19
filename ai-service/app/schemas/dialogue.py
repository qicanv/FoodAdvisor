from enum import Enum
from math import isfinite
from typing import Any, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


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
    "scenes",
    "environmentRequirements",
    "businessTime",
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
    scenes: list[str] = Field(default_factory=list)
    environmentRequirements: list[str] = Field(default_factory=list)
    businessTime: Optional[str] = None

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


class DialogueExtractRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sessionId: int = Field(gt=0)
    messageId: int = Field(gt=0)
    content: str
    currentConstraints: ConstraintStateModel = Field(
        default_factory=ConstraintStateModel
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
