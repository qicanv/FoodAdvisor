import pytest

from app.core.config import settings
from app.models.schemas import (
    AnalyzeRequest,
    CompetitorComparisonRequest,
    CompetitorMerchantData,
    GenerateReplyRequest,
    ReplyStrategyEnum,
    ReviewSummaryRequest,
    SummaryReviewItem,
)
from app.schemas.dialogue import DialogueExtractRequest
from app.services import competitor_comparison_service as competitor_module
from app.services import dialogue_extraction_service as dialogue_module
from app.services import reply_draft_service as reply_module
from app.services import review_analysis_service as analysis_module
from app.services import review_summary_service as summary_module


@pytest.mark.asyncio
async def test_sentiment_analysis_uses_runtime_prompt(monkeypatch):
    captured = {}

    async def fake_chat_json(**kwargs):
        captured.update(kwargs)
        return {
            "sentiment": "POSITIVE",
            "confidence": 0.9,
            "keywords": ["口味"],
            "aspects": [],
            "tags": [],
            "issueCategories": [],
            "negativeReason": None,
        }

    monkeypatch.setattr(
        settings,
        "sentiment_analysis_mode",
        "llm",
    )
    monkeypatch.setattr(
        analysis_module.llm_service,
        "chat_json",
        fake_chat_json,
    )

    request = AnalyzeRequest(
        reviewId=1,
        merchantId=10,
        content="菜品味道很好",
        systemPrompt="自定义情感分析提示词",
        promptVersion="sentiment-custom:v3",
    )

    response = await analysis_module.review_analysis_service.analyze(
        request
    )

    assert captured["system_prompt"] == "自定义情感分析提示词"
    assert response.promptVersion == "sentiment-custom:v3"


@pytest.mark.asyncio
async def test_review_summary_uses_runtime_prompt(monkeypatch):
    captured = {}

    async def fake_chat_json(**kwargs):
        captured.update(kwargs)
        return {
            "summaryText": "整体评价较好",
            "advantages": [],
            "disadvantages": [],
            "recommendedDishes": [],
            "environmentSummary": {},
            "serviceSummary": {},
            "recentChanges": [],
            "evidences": [],
        }

    monkeypatch.setattr(
        summary_module.llm_service,
        "chat_json",
        fake_chat_json,
    )

    request = ReviewSummaryRequest(
        merchantId=10,
        minimumReviewCount=1,
        reviews=[
            SummaryReviewItem(
                reviewId=1,
                rating=5,
                content="菜品味道很好",
            )
        ],
        systemPrompt="自定义评价摘要提示词",
        promptVersion="summary-custom:v2",
    )

    response = await summary_module.review_summary_service.summarize(
        request
    )

    assert captured["system_prompt"] == "自定义评价摘要提示词"
    assert response.promptVersion == "summary-custom:v2"


@pytest.mark.asyncio
async def test_dialogue_extraction_uses_runtime_prompt(monkeypatch):
    captured = {}

    async def fake_chat_json(**kwargs):
        captured.update(kwargs)
        return {
            "intent": "MERCHANT_RECOMMENDATION",
            "extractedConstraints": {
                "cuisines": ["川菜"],
            },
            "clearedFields": [],
            "confidence": 0.9,
        }

    monkeypatch.setattr(
        dialogue_module.llm_service,
        "is_configured",
        lambda: True,
    )
    monkeypatch.setattr(
        dialogue_module.llm_service,
        "chat_json",
        fake_chat_json,
    )

    request = DialogueExtractRequest(
        sessionId=1,
        messageId=2,
        content="想吃川菜",
        systemPrompt="自定义需求提取提示词",
        promptVersion="constraint-custom:v4",
    )

    response = await dialogue_module.dialogue_extraction_service.extract(
        request
    )

    assert captured["system_prompt"] == "自定义需求提取提示词"
    assert response.promptVersion == "constraint-custom:v4"


@pytest.mark.asyncio
async def test_review_reply_uses_runtime_prompt(monkeypatch):
    captured = {}

    async def fake_chat(**kwargs):
        captured.update(kwargs)
        return "感谢您的认可，期待您再次光临。"

    monkeypatch.setattr(
        reply_module.reply_draft_service.llm,
        "chat",
        fake_chat,
    )

    request = GenerateReplyRequest(
        reviewId=1,
        merchantId=10,
        content="味道很好，服务也不错",
        strategy=ReplyStrategyEnum.POSITIVE,
        rating=5,
        systemPrompt="自定义评价回复提示词",
        promptVersion="reply-custom:v5",
    )

    response = await reply_module.reply_draft_service.generate(
        request
    )

    assert captured["system_prompt"] == "自定义评价回复提示词"
    assert response.promptVersion == "reply-custom:v5"
    assert response.status == "SUCCESS"


@pytest.mark.asyncio
async def test_business_advice_uses_runtime_prompt(monkeypatch):
    captured = {}

    async def fake_chat_json(**kwargs):
        captured.update(kwargs)
        return {
            "merchantAnalyses": [
                {
                    "merchantId": 1,
                    "merchantName": "本店",
                    "strengths": [],
                    "weaknesses": [],
                    "overallAssessment": "整体表现稳定",
                },
                {
                    "merchantId": 2,
                    "merchantName": "竞品",
                    "strengths": [],
                    "weaknesses": [],
                    "overallAssessment": "与本店差异不大",
                },
            ],
            "summaryText": "两家商家整体差异不大",
            "improvementSuggestions": [],
        }

    monkeypatch.setattr(
        competitor_module.llm_service,
        "chat_json",
        fake_chat_json,
    )

    request = CompetitorComparisonRequest(
        merchantId=1,
        competitors=[
            CompetitorMerchantData(
                merchantId=1,
                merchantName="本店",
                category="川菜",
                rating=4.3,
                reviewCount=100,
            ),
            CompetitorMerchantData(
                merchantId=2,
                merchantName="竞品",
                category="川菜",
                rating=4.2,
                reviewCount=90,
            ),
        ],
        systemPrompt="自定义经营建议提示词",
        promptVersion="advice-custom:v6",
    )

    response = await (
        competitor_module.competitor_comparison_service.compare(
            request
        )
    )

    assert captured["system_prompt"] == "自定义经营建议提示词"
    assert response.promptVersion == "advice-custom:v6"
    assert response.comparisonStatus == "SUCCESS"