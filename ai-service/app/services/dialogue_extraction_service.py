import json
from typing import Any

from fastapi import HTTPException, status
from pydantic import ValidationError

from app.schemas.dialogue import (
    DialogueExtractRequest,
    DialogueExtractResponse,
)
from app.services.llm_service import llm_service


SYSTEM_PROMPT = """
You extract dining dialogue intent and consumer constraints for FoodAdvisor.
Return strict JSON only. Do not use Markdown or code blocks.

The JSON must use this exact top-level structure:
{
  "intent": "MERCHANT_RECOMMENDATION",
  "extractedConstraints": {},
  "clearedFields": [],
  "confidence": 0.0
}

Allowed top-level fields:
intent, extractedConstraints, clearedFields, confidence.

Allowed intents:
MERCHANT_RECOMMENDATION, CONSTRAINT_UPDATE, GENERAL_CHAT, UNKNOWN.

Allowed constraint fields:
partySize, totalBudget, perCapitaBudget, merchantTypes, cuisines,
tastePreferences, tasteRestrictions, dishKeywords, excludedCuisines,
excludedMerchantTypes, distanceKm, minRating, scenes,
environmentRequirements, businessTime, businessTargetTime,
businessTargetNextDay.

Rules:
- Always use the top-level field extractedConstraints.
- Never use constraints as a top-level field.
- Do not recommend merchants.
- Do not output merchantId or merchant names.
- Do not invent constraints the user did not express.
- Missing constraint fields must be null or omitted.
- Put explicitly removed conditions into clearedFields.
- Only use allowed top-level fields and allowed constraint fields.
- Do not output latitude or longitude.
- Put explicit dishes or main ingredients in dishKeywords.
- Never put negated foods such as "不吃香菜" in dishKeywords.
- Do not treat cuisines, budgets, distances, or party size as dishes.
- confidence must be a number between 0 and 1.
"""


def normalize_model_result(result: Any) -> dict[str, Any]:
    """
    Normalize a small number of known model-output aliases before strict
    Pydantic validation.

    The official response field remains extractedConstraints. Some models may
    return constraints despite the prompt, so it is converted here.
    """
    if not isinstance(result, dict):
        raise ValueError(
            f"Dialogue extraction result must be a JSON object, "
            f"but received {type(result).__name__}"
        )

    normalized = dict(result)

    if "constraints" in normalized:
        if (
            "extractedConstraints" not in normalized
            or normalized["extractedConstraints"] is None
        ):
            normalized["extractedConstraints"] = normalized["constraints"]

        # Remove the non-standard field so that extra="forbid" validation
        # continues to reject other unexpected fields.
        normalized.pop("constraints", None)

    return normalized


class DialogueExtractionService:
    async def extract(
        self,
        request: DialogueExtractRequest,
    ) -> DialogueExtractResponse:
        if not llm_service.is_configured():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="LLM model is not configured",
            )

        payload = {
            "sessionId": request.sessionId,
            "messageId": request.messageId,
            "content": request.content,
            "currentConstraints": request.currentConstraints.model_dump(
                exclude_none=True
            ),
        }

        try:
            result = await llm_service.chat_json(
                SYSTEM_PROMPT,
                json.dumps(payload, ensure_ascii=False),
                temperature=0.1,
                max_tokens=1200,
            )

            normalized_result = normalize_model_result(result)
            response = DialogueExtractResponse.model_validate(
                normalized_result
            )

            response.extractor = "AI_MODEL"
            response.degraded = False
            response.modelName = llm_service.model
            response.provider = llm_service.provider

            return response

        except (ValueError, ValidationError) as exception:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=(
                    "Invalid dialogue extraction result: "
                    f"{exception}"
                ),
            ) from exception

        except HTTPException:
            raise

        except Exception as exception:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Dialogue extraction model call failed",
            ) from exception


dialogue_extraction_service = DialogueExtractionService()
