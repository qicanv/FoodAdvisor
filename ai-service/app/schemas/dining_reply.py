from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class DiningCandidateFacts(BaseModel):
    model_config = ConfigDict(extra="forbid")

    merchantId: int
    name: str
    facts: dict[str, str] = Field(default_factory=dict)
    riskFacts: dict[str, str] = Field(default_factory=dict)
    evidenceIds: list[int] = Field(default_factory=list)


class DiningGapFact(BaseModel):
    model_config = ConfigDict(extra="forbid")

    factId: str
    field: str
    description: str
    currentValue: Any | None = None
    nearestCandidateValue: Any | None = None
    difference: Any | None = None
    recoveredMerchantCount: int = 0
    candidateMerchantIds: list[int] = Field(default_factory=list)


class DiningReplyRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    mode: Literal["CLARIFICATION", "RECOMMENDATION", "NO_MATCH"]
    currentConstraints: dict = Field(default_factory=dict)
    changedFields: list[str] = Field(default_factory=list)
    missingFields: list[str] = Field(default_factory=list)
    conflicts: list[dict] = Field(default_factory=list)
    candidates: list[DiningCandidateFacts] = Field(default_factory=list)
    gapFacts: list[DiningGapFact] = Field(default_factory=list)
    recentMessages: list[dict[str, str]] = Field(default_factory=list)
    maximumQuestions: int = Field(default=2, ge=0, le=2)
    runtimeModel: dict
    systemPrompt: str | None = None
    promptVersion: str | None = None


class MerchantReason(BaseModel):
    model_config = ConfigDict(extra="forbid")

    merchantId: int
    reason: str
    factIds: list[str] = Field(default_factory=list)
    riskFactIds: list[str] = Field(default_factory=list)
    evidenceIds: list[int] = Field(default_factory=list)


class DiningReply(BaseModel):
    model_config = ConfigDict(extra="forbid")

    assistantText: str
    merchantReasons: list[MerchantReason] = Field(default_factory=list)
    followUpQuestions: list[str] = Field(default_factory=list, max_length=2)
    usedFactIds: list[str] = Field(default_factory=list)
    usedEvidenceIds: list[int] = Field(default_factory=list)
    replyGenerator: Literal["AI_MODEL", "TEMPLATE_FALLBACK"] = "AI_MODEL"
    degraded: bool = False
    modelName: str | None = None
    promptVersion: str | None = None
