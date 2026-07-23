import json

import pytest
from langchain_core.language_models.fake_chat_models import FakeListChatModel
from langchain_core.exceptions import OutputParserException

from app.chains.dining_constraint_chain import DiningConstraintChain
from app.chains.dining_reply_chain import DiningReplyChain
from app.schemas.dining_reply import DiningReplyRequest
from app.services.dining_reply_service import _validate_references


def patch_json() -> str:
    return json.dumps(
        {
            "intent": "CONSTRAINT_UPDATE",
            "directRecommend": False,
            "operations": {
                "set": {"partySize": 3},
                "add": {"cuisines": ["烧烤"]},
                "remove": {"environmentRequirements": ["安静"]},
                "clear": ["tastePreferences"],
                "exclude": {"cuisines": ["川菜"]},
                "unexclude": {"merchantTypes": ["火锅"]},
            },
            "conflicts": [],
            "followUpHints": [],
            "confidence": {"partySize": 0.95},
        },
        ensure_ascii=False,
    )


@pytest.mark.asyncio
async def test_constraint_chain_outputs_all_patch_operations():
    patch = await DiningConstraintChain(
        FakeListChatModel(responses=[patch_json()])
    ).ainvoke({"content": "改成烧烤，不要川菜，也不要求安静"})

    assert patch.operations.set_values == {"partySize": 3}
    assert patch.operations.add["cuisines"] == ["烧烤"]
    assert patch.operations.remove["environmentRequirements"] == ["安静"]
    assert patch.operations.clear == ["tastePreferences"]
    assert patch.operations.exclude["cuisines"] == ["川菜"]
    assert patch.operations.unexclude["merchantTypes"] == ["火锅"]


@pytest.mark.asyncio
async def test_constraint_chain_repairs_invalid_json_once():
    patch = await DiningConstraintChain(
        FakeListChatModel(responses=["not-json", patch_json()])
    ).ainvoke({"content": "三个人吃烧烤"})
    assert patch.operations.set_values["partySize"] == 3


@pytest.mark.asyncio
async def test_constraint_chain_repair_failure_is_controlled():
    with pytest.raises(OutputParserException):
        await DiningConstraintChain(
            FakeListChatModel(responses=["bad", "still bad"])
        ).ainvoke({"content": "三个人"})


@pytest.mark.asyncio
async def test_reply_chain_uses_structured_fact_references():
    response = json.dumps(
        {
            "assistantText": "这家店的菜系和预算都符合。",
            "merchantReasons": [
                {
                    "merchantId": 12,
                    "reason": "菜系和预算符合",
                    "factIds": ["m12.cuisine", "m12.price"],
                    "riskFactIds": [],
                    "evidenceIds": [101],
                }
            ],
            "followUpQuestions": [],
            "usedFactIds": ["m12.cuisine", "m12.price"],
            "usedEvidenceIds": [101],
            "replyGenerator": "AI_MODEL",
            "degraded": False,
        },
        ensure_ascii=False,
    )
    reply = await DiningReplyChain(
        FakeListChatModel(responses=[response])
    ).ainvoke({"mode": "RECOMMENDATION"})
    request = DiningReplyRequest.model_validate(
        {
            "mode": "RECOMMENDATION",
            "candidates": [
                {
                    "merchantId": 12,
                    "name": "示例店",
                    "facts": {
                        "m12.cuisine": "菜系为川菜",
                        "m12.price": "人均80元",
                    },
                    "riskFacts": {},
                    "evidenceIds": [101],
                }
            ],
            "runtimeModel": {
                "provider": "fake",
                "modelName": "fake",
                "baseUrl": "https://example.invalid/v1",
                "apiKey": "fake",
                "timeoutMs": 1000,
                "temperature": 0,
                "maxOutputTokens": 500,
            },
        }
    )
    _validate_references(request, reply)


def test_reply_rejects_cross_merchant_evidence():
    from app.schemas.dining_reply import DiningReply

    request = DiningReplyRequest.model_validate(
        {
            "mode": "RECOMMENDATION",
            "candidates": [{
                "merchantId": 12,
                "name": "示例店",
                "facts": {"m12.cuisine": "川菜"},
                "evidenceIds": [101],
            }],
            "runtimeModel": {
                "provider": "fake", "modelName": "fake",
                "baseUrl": "https://example.invalid/v1", "apiKey": "fake",
                "timeoutMs": 1000, "temperature": 0,
                "maxOutputTokens": 500,
            },
        }
    )
    reply = DiningReply.model_validate({
        "assistantText": "非法引用",
        "merchantReasons": [{
            "merchantId": 12,
            "reason": "非法",
            "factIds": ["m12.cuisine"],
            "evidenceIds": [999],
        }],
        "usedEvidenceIds": [999],
    })
    with pytest.raises(ValueError):
        _validate_references(request, reply)
