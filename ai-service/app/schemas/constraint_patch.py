from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


SCALAR_FIELDS = {
    "partySize",
    "totalBudget",
    "perCapitaBudget",
    "distanceKm",
    "minRating",
    "ratingPreference",
    "businessTime",
    "businessTargetTime",
    "businessTargetNextDay",
    "businessTargetDate",
    "businessTargetDayOfWeek",
    "businessTimeWindow",
    "timezone",
}

LIST_FIELDS = {
    "merchantTypes",
    "cuisines",
    "tastePreferences",
    "tasteRestrictions",
    "dishKeywords",
    "excludedCuisines",
    "excludedMerchantTypes",
    "scenes",
    "environmentRequirements",
}

ALL_FIELDS = SCALAR_FIELDS | LIST_FIELDS


class ConstraintPatchOperations(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    set_values: dict[str, Any] = Field(default_factory=dict, alias="set")
    add: dict[str, list[str]] = Field(default_factory=dict)
    remove: dict[str, list[str]] = Field(default_factory=dict)
    clear: list[str] = Field(default_factory=list)
    exclude: dict[str, list[str]] = Field(default_factory=dict)
    unexclude: dict[str, list[str]] = Field(default_factory=dict)

    @field_validator("set_values")
    @classmethod
    def validate_set_fields(cls, value: dict[str, Any]) -> dict[str, Any]:
        unknown = set(value) - ALL_FIELDS
        if unknown:
            raise ValueError(f"unsupported set fields: {sorted(unknown)}")
        if (
            "ratingPreference" in value
            and value["ratingPreference"] != "HIGH"
        ):
            raise ValueError("ratingPreference only supports HIGH")
        return value

    @field_validator("add", "remove", "exclude", "unexclude")
    @classmethod
    def validate_list_operations(
        cls, value: dict[str, list[str]]
    ) -> dict[str, list[str]]:
        unknown = set(value) - LIST_FIELDS
        if unknown:
            raise ValueError(f"unsupported list fields: {sorted(unknown)}")
        return value

    @field_validator("clear")
    @classmethod
    def validate_clear_fields(cls, value: list[str]) -> list[str]:
        unknown = set(value) - ALL_FIELDS
        if unknown:
            raise ValueError(f"unsupported clear fields: {sorted(unknown)}")
        return list(dict.fromkeys(value))


class ConstraintPatchConflict(BaseModel):
    model_config = ConfigDict(extra="forbid")

    field: str
    message: str
    values: list[str] = Field(default_factory=list)

    @field_validator("field")
    @classmethod
    def validate_conflict_field(cls, value: str) -> str:
        if value not in ALL_FIELDS:
            raise ValueError(f"unsupported conflict field: {value}")
        return value


class ConstraintPatch(BaseModel):
    model_config = ConfigDict(extra="forbid")

    intent: Literal[
        "MERCHANT_RECOMMENDATION",
        "CONSTRAINT_UPDATE",
        "GENERAL_CHAT",
        "UNKNOWN",
    ] = "MERCHANT_RECOMMENDATION"
    directRecommend: bool = False
    operations: ConstraintPatchOperations = Field(
        default_factory=ConstraintPatchOperations
    )
    conflicts: list[ConstraintPatchConflict] = Field(default_factory=list)
    followUpHints: list[str] = Field(default_factory=list)
    confidence: dict[str, float] = Field(default_factory=dict)

    @field_validator("confidence")
    @classmethod
    def validate_confidence(cls, value: dict[str, float]) -> dict[str, float]:
        for field_name, score in value.items():
            if field_name not in ALL_FIELDS:
                raise ValueError(f"unsupported confidence field: {field_name}")
            if score < 0 or score > 1:
                raise ValueError("confidence values must be between 0 and 1")
        return value
