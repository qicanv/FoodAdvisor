import json

import httpx

from app.core.config import settings
from app.main import app
from app.services import review_analysis_service as service_module


async def test_review(monkeypatch):
    settings.internal_api_token = "test-token"

    async def chat_json(*args, **kwargs):
        return {
            "sentiment": "POSITIVE",
            "confidence": 0.9,
            "keywords": ["taste"],
            "aspects": [
                {
                    "category": "TASTE",
                    "sentiment": "POSITIVE",
                    "text": "good taste",
                }
            ],
            "tags": [],
            "issueCategories": [],
            "negativeReason": None,
        }

    monkeypatch.setattr(
        service_module.llm_service,
        "chat_json",
        chat_json,
    )

    headers = {"X-Internal-Token": "test-token"}
    transport = httpx.ASGITransport(app=app)

    async with httpx.AsyncClient(
        transport=transport,
        base_url="http://testserver",
        trust_env=False,
        timeout=60.0,
    ) as client:
        resp = await client.get("/health")
        print("=== health ===")
        print("status:", resp.status_code)
        if resp.status_code == 200:
            print(resp.text)

        print("\n=== positive review analysis ===")
        resp = await client.post(
            "/internal/reviews/analyze",
            headers=headers,
            json={
                "reviewId": 1,
                "merchantId": 1,
                "content": "taste is very good",
            },
        )
        assert resp.status_code == 200
        print(json.dumps(resp.json(), ensure_ascii=False, indent=2))

        print("\n=== negative review analysis ===")
        resp = await client.post(
            "/internal/reviews/analyze",
            headers=headers,
            json={
                "reviewId": 4,
                "merchantId": 1,
                "content": "service was slow",
            },
        )
        assert resp.status_code == 200
        print(json.dumps(resp.json(), ensure_ascii=False, indent=2))
