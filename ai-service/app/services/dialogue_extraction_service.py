import json

from fastapi import HTTPException, status
from pydantic import ValidationError

from app.schemas.dialogue import (
    DialogueExtractRequest,
    DialogueExtractResponse,
)
from app.services.llm_service import llm_service


SYSTEM_PROMPT = """
You are a dining constraint extraction system for FoodAdvisor.
You must return ONLY a valid JSON object, no markdown, no extra text.

The JSON MUST have this structure:
{
  "intent": "MERCHANT_RECOMMENDATION",
  "extractedConstraints": {
    "partySize": null,
    "totalBudget": null,
    "perCapitaBudget": null,
    "merchantTypes": [],
    "cuisines": [],
    "tastePreferences": [],
    "tasteRestrictions": [],
    "excludedCuisines": [],
    "excludedMerchantTypes": [],
    "distanceKm": null,
    "minRating": null,
    "scenes": [],
    "environmentRequirements": [],
    "businessTime": null
  },
  "clearedFields": [],
  "confidence": 1.0,
  "extractor": "AI_MODEL",
  "degraded": false
}

Allowed "intent" values (exact match required):
- MERCHANT_RECOMMENDATION: user wants restaurant recommendations
- CONSTRAINT_UPDATE: user is updating their constraints
- GENERAL_CHAT: casual chat, not about finding restaurants
- UNKNOWN: cannot determine intent

Rules:
1. intent MUST be one of the 4 values above. Do NOT invent new intents.
2. extractedConstraints: only fill fields the user actually mentioned. Leave others as null or empty [].
3. "两个人100元火锅" → partySize=2, totalBudget=100, merchantTypes=["火锅"]
4. "别太远，5公里以内吧" → distanceKm=5
5. "不要火锅" → excludedCuisines=["火锅"] OR clearedFields=["cuisines"] if previously set
6. clearedFields: list fields the user wants to remove/reset.
7. confidence: 0-1, your confidence in this extraction.
8. Do NOT output merchantId, merchantName, latitude, or longitude.
"""


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
            return DialogueExtractResponse.model_validate(result)
        except (ValueError, ValidationError) as exception:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Invalid dialogue extraction result: {exception}",
            ) from exception
        except HTTPException:
            raise
        except Exception as exception:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Dialogue extraction model call failed",
            ) from exception


dialogue_extraction_service = DialogueExtractionService()
