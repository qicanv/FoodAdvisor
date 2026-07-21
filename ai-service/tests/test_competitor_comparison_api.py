"""
周边竞品对比 API 集成测试（EPIC-02 Story 6）

使用 FastAPI TestClient 测试 /internal/merchants/competitor-comparison 端点。
"""
import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.services import competitor_comparison_service as service_module

client = TestClient(app)

# 合法的测试请求体（本店+2家竞品）
VALID_BODY = {
    "merchantId": 1,
    "competitors": [
        {
            "merchantId": 1, "merchantName": "本店", "category": "火锅",
            "cuisine": "川味", "address": "地址1", "averagePrice": 80.0,
            "rating": 4.2, "reviewCount": 200, "positiveRate": 0.85,
            "tasteRating": 4.3, "environmentRating": 4.1, "serviceRating": 4.0,
            "topPositiveTags": ["口味好"], "topNegativeIssues": ["排队久"],
        },
        {
            "merchantId": 2, "merchantName": "竞品A", "category": "火锅",
            "cuisine": "川味", "address": "地址2", "averagePrice": 95.0,
            "rating": 4.5, "reviewCount": 350, "positiveRate": 0.92,
            "tasteRating": 4.6, "environmentRating": 4.4, "serviceRating": 4.5,
            "topPositiveTags": ["口味好", "环境好"], "topNegativeIssues": ["价格偏高"],
        },
        {
            "merchantId": 3, "merchantName": "竞品B", "category": "火锅",
            "cuisine": "川味", "address": "地址3", "averagePrice": 65.0,
            "rating": 3.8, "reviewCount": 120, "positiveRate": 0.72,
            "tasteRating": 3.9, "environmentRating": 3.7, "serviceRating": 3.5,
            "topPositiveTags": ["性价比高"], "topNegativeIssues": ["口味差", "环境差"],
        },
    ],
}

def auth_headers() -> dict[str, str]:
    return {
        "X-Internal-Token": settings.internal_api_token or "",
        "X-Request-Id": "test-competitor-001",
    }

# 当 internal_api_token 未配置时跳过需要认证的测试
requires_token = pytest.mark.skipif(
    not settings.internal_api_token,
    reason="未配置 INTERNAL_API_TOKEN",
)


@requires_token
def test_competitor_comparison_accepts_valid_request(monkeypatch):
    """正常请求：3家商家（含本店），应成功返回 AI 分析。"""
    async def fake_chat_json(**_kwargs):
        return {
            "merchantAnalyses": [
                {"merchantId": 1, "strengths": ["价格实惠"],
                 "weaknesses": [], "overallAssessment": "不错"},
                {"merchantId": 2, "strengths": ["评分最高"],
                 "weaknesses": ["价格高"], "overallAssessment": "高端"},
                {"merchantId": 3, "strengths": ["价格最低"],
                 "weaknesses": ["评分低"], "overallAssessment": "低端"},
            ],
            "summaryText": "测试总结",
            "improvementSuggestions": ["建议1"],
        }

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json)

    response = client.post(
        "/internal/merchants/competitor-comparison",
        headers=auth_headers(),
        json=VALID_BODY,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["comparisonStatus"] == "SUCCESS"
    assert body["merchantId"] == 1
    assert len(body["merchantAnalyses"]) == 3
    assert body["summaryText"] == "测试总结"
    assert len(body["improvementSuggestions"]) == 1
    assert body["modelName"] is not None
    assert body["businessTraceId"] is not None


@requires_token
def test_competitor_comparison_rejects_less_than_2_merchants():
    """边界校验：仅有本店（1家），应返回422。"""
    body = {
        "merchantId": 1,
        "competitors": [
            {"merchantId": 1, "merchantName": "本店", "category": "火锅",
             "reviewCount": 10, "topPositiveTags": [], "topNegativeIssues": []},
        ],
    }
    response = client.post(
        "/internal/merchants/competitor-comparison",
        headers=auth_headers(),
        json=body,
    )
    assert response.status_code == 422


@requires_token
def test_competitor_comparison_rejects_more_than_4_merchants():
    """边界校验：超过4家商家，应返回422。"""
    body = {
        "merchantId": 1,
        "competitors": [
            {"merchantId": i, "merchantName": f"商家{i}", "category": "火锅",
             "reviewCount": 10, "topPositiveTags": [], "topNegativeIssues": []}
            for i in range(1, 6)
        ],
    }
    response = client.post(
        "/internal/merchants/competitor-comparison",
        headers=auth_headers(),
        json=body,
    )
    assert response.status_code == 422


@requires_token
def test_competitor_comparison_rejects_self_not_in_competitors():
    """安全校验：本店不在竞品列表中，应返回422。"""
    body = {
        "merchantId": 1,
        "competitors": [
            {"merchantId": 2, "merchantName": "竞品A", "category": "火锅",
             "reviewCount": 10, "topPositiveTags": [], "topNegativeIssues": []},
            {"merchantId": 3, "merchantName": "竞品B", "category": "火锅",
             "reviewCount": 10, "topPositiveTags": [], "topNegativeIssues": []},
        ],
    }
    response = client.post(
        "/internal/merchants/competitor-comparison",
        headers=auth_headers(),
        json=body,
    )
    assert response.status_code == 422


@requires_token
def test_competitor_comparison_rejects_wrong_token():
    """认证校验：错误的 internal token，应返回401。"""
    response = client.post(
        "/internal/merchants/competitor-comparison",
        headers={"X-Internal-Token": "wrong-token"},
        json=VALID_BODY,
    )
    assert response.status_code == 401


@requires_token
def test_competitor_comparison_llm_failure_graceful_degradation(monkeypatch):
    """容错场景：LLM 调用异常时返回 FAILED。"""
    async def fake_chat_json_error(**_kwargs):
        raise RuntimeError("模拟API调用失败")

    monkeypatch.setattr(
        service_module.llm_service, "chat_json", fake_chat_json_error
    )

    response = client.post(
        "/internal/merchants/competitor-comparison",
        headers=auth_headers(),
        json=VALID_BODY,
    )

    assert response.status_code == 200
    body = response.json()
    assert body["comparisonStatus"] == "FAILED"
    assert "模拟API调用失败" in (body.get("errorMessage") or "")
