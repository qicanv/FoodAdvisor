"""
评论摘要忠实性测试 — API 集成测试（EPIC-06 Story 3）

通过 FastAPI TestClient 测试 HTTP 端点行为：
  - 正常请求/响应
  - 认证失败 → 401
  - 参数校验（空 reviews / 空 summary）
  - 摘要状态非 SUCCESS 时提前返回
  - 响应结构与 schema 一致性
"""
import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.models.schemas import (
    FaithfulnessTestRequest,
    FaithfulnessTestResponse,
    FaithfulnessClaimResult,
    FaithfulnessVerdictEnum,
)
from app.services import faithfulness_test_service as service_module


client = TestClient(app)


def _auth_headers() -> dict:
    """
    动态读取当前生效的 internal_api_token。

    注意：不能使用模块级常量！因为 test_dialogue_extract.py 会在运行期
    直接修改 settings.internal_api_token，模块级常量在 import 时就已固化，
    导致后续测试的 token 与服务器端不一致 → 401。
    """
    return {
        "X-Internal-Token": settings.internal_api_token,
        "X-Request-Id": "req-test-faithfulness-001",
    }

# ============================================
# 测试数据
# ============================================

VALID_SUMMARY = {
    "merchantId": 10,
    "version": 1,
    "summaryStatus": "SUCCESS",
    "summaryText": "总体口碑良好，菜品口味受好评",
    "advantages": [
        {"name": "菜品口味好", "mentionCount": 2, "reviewIds": [101, 102]},
    ],
    "disadvantages": [
        {"name": "排队时间久", "mentionCount": 1, "reviewIds": [103]},
    ],
    "recommendedDishes": [],
    "environmentSummary": {},
    "serviceSummary": {},
    "recentChanges": [],
    "reviewCount": 3,
    "minimumReviewCount": 3,
    "evidences": [],
}

VALID_REVIEWS = [
    {"reviewId": 101, "content": "红烧肉特别入味，每次来必点", "rating": 5},
    {"reviewId": 102, "content": "口味确实不错，推荐", "rating": 4},
    {"reviewId": 103, "content": "周末去排了四十分钟才吃上", "rating": 3},
]

VALID_REQUEST_BODY = {
    "requestId": "req-test-001",
    "merchantId": 10,
    "summary": VALID_SUMMARY,
    "reviews": VALID_REVIEWS,
}

# ============================================
# mock LLM 返回的辅助
# ============================================

def _mock_llm_all_faithful(monkeypatch):
    """注入断言全部 FAITHFUL 的 LLM 返回值。"""
    async def fake_chat_json(**_kwargs):
        return {
            "claimResults": [
                {"claimIndex": 0, "verdict": "FAITHFUL", "confidence": 0.95,
                 "reasoning": "评价[reviewId=101]提到'红烧肉特别入味'，评价[reviewId=102]提到'口味确实不错'"},
                {"claimIndex": 1, "verdict": "FAITHFUL", "confidence": 0.90,
                 "reasoning": "评价[reviewId=103]提到'排了四十分钟'，排队时间久是事实"},
                {"claimIndex": 2, "verdict": "FAITHFUL", "confidence": 0.88,
                 "reasoning": "所有评价整体正面，总体概述与评价一致"},
            ],
        }

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json)


def _mock_llm_failure(monkeypatch):
    """注入 LLM 调用失败的异常。"""
    async def fake_chat_json_error(**_kwargs):
        raise RuntimeError("模拟 LLM 服务不可用")

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json_error)


# ============================================
# 正常请求
# ============================================

def test_full_faithfulness_test_success(monkeypatch):
    """
    正常请求：有效数据 + LLM 全部判定 FAITHFUL，
    返回 SUCCESS 状态、满分、完整结构。
    """
    _mock_llm_all_faithful(monkeypatch)

    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json=VALID_REQUEST_BODY,
    )

    assert response.status_code == 200
    body = response.json()

    assert body["testStatus"] == "SUCCESS"
    assert body["overallScore"] == 1.0
    assert body["totalClaims"] > 0
    assert body["faithfulCount"] == body["totalClaims"]
    assert body["unfaithfulCount"] == 0
    assert body["uncertainCount"] == 0
    assert body["merchantId"] == 10
    assert body["summaryText"] == "总体口碑良好，菜品口味受好评"
    assert body["modelName"] is not None
    assert body["businessTraceId"] is not None

    # claimResults 结构完整性
    for claim in body["claimResults"]:
        assert "claimType" in claim
        assert "claimText" in claim
        assert "verdict" in claim
        assert "confidence" in claim
        assert "reasoning" in claim
        assert "citedReviewIds" in claim
        assert "actualMatchingCount" in claim


def test_faithfulness_test_with_all_claim_types(monkeypatch):
    """
    多声明类型：验证 advantage/disadvantage/dish/environment/service/change
    全部出现在响应中。
    """
    full_summary = {
        "merchantId": 10,
        "version": 1,
        "summaryStatus": "SUCCESS",
        "summaryText": "整体不错的一家店",
        "advantages": [{"name": "口味好", "mentionCount": 2, "reviewIds": [101, 102]}],
        "disadvantages": [{"name": "分量少", "mentionCount": 1, "reviewIds": [103]}],
        "recommendedDishes": [{"name": "红烧肉", "mentionCount": 2, "reviewIds": [101, 102]}],
        "environmentSummary": {"text": "环境安静", "reviewIds": [101]},
        "serviceSummary": {"text": "服务周到", "reviewIds": [102]},
        "recentChanges": [{"text": "近期上菜变慢", "direction": "DECLINING", "reviewIds": [103]}],
        "reviewCount": 3,
        "minimumReviewCount": 3,
        "evidences": [],
    }

    # 构造足够的 FAITHFUL 判定（7 个声明）
    claim_results = [
        {"claimIndex": i, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "ok"}
        for i in range(7)
    ]

    async def fake_chat_json(**_kwargs):
        return {"claimResults": claim_results}

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json)

    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json={
            "requestId": "req-test-002",
            "merchantId": 10,
            "summary": full_summary,
            "reviews": VALID_REVIEWS,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["totalClaims"] == 7
    assert body["overallScore"] == 1.0

    types = {c["claimType"] for c in body["claimResults"]}
    assert types == {
        "advantage", "disadvantage", "recommendedDish",
        "environmentSummary", "serviceSummary", "recentChange",
        "summaryText",
    }


# ============================================
# 认证失败
# ============================================

def test_rejects_missing_token():
    """
    认证场景：缺少 X-Internal-Token → 401。
    """
    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        json=VALID_REQUEST_BODY,
    )

    assert response.status_code == 401
    body = response.json()
    assert body["status"] == "FAILED"
    assert body["error"]["code"] == "UNAUTHORIZED"


def test_rejects_wrong_token():
    """
    认证场景：错误的 X-Internal-Token → 401。
    """
    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers={"X-Internal-Token": "definitely-wrong-token"},
        json=VALID_REQUEST_BODY,
    )

    assert response.status_code == 401
    body = response.json()
    assert body["status"] == "FAILED"
    assert body["error"]["code"] == "UNAUTHORIZED"


# ============================================
# 参数校验
# ============================================

def test_rejects_empty_reviews():
    """
    参数校验：reviews 为空 → 422。
    """
    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json={
            "requestId": "req-test-003",
            "merchantId": 10,
            "summary": VALID_SUMMARY,
            "reviews": [],
        },
    )

    assert response.status_code == 422


def test_rejects_missing_summary():
    """
    参数校验：summary 为 None → 422（Pydantic 校验失败或路由层检查）。
    """
    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json={
            "requestId": "req-test-004",
            "merchantId": 10,
            "summary": None,
            "reviews": VALID_REVIEWS,
        },
    )

    # Pydantic 不接受 null，或路由层捕捉到
    assert response.status_code in (422, 400)


def test_rejects_empty_review_content():
    """
    参数校验：reviews 中某条评价 content 为空 → 422。
    """
    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json={
            "requestId": "req-test-005",
            "merchantId": 10,
            "summary": VALID_SUMMARY,
            "reviews": [
                {"reviewId": 101, "content": "", "rating": 4},  # 空内容
            ],
        },
    )

    assert response.status_code == 422


def test_rejects_missing_merchant_id():
    """
    参数校验：缺少 merchantId → 422。
    """
    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json={
            "requestId": "req-test-006",
            # 缺少 merchantId
            "summary": VALID_SUMMARY,
            "reviews": VALID_REVIEWS,
        },
    )

    assert response.status_code == 422


# ============================================
# 摘要状态非 SUCCESS → 提前返回
# ============================================

def test_insufficient_data_summary_skips_test(monkeypatch):
    """
    逻辑分支：摘要状态为 INSUFFICIENT_DATA 时，不调用 LLM 直接返回。
    """
    insufficient_summary = dict(VALID_SUMMARY)
    insufficient_summary["summaryStatus"] = "INSUFFICIENT_DATA"

    # 不 mock LLM —— 如果调用了 LLM 会因为没有 mock 而报错
    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json={
            "requestId": "req-test-007",
            "merchantId": 10,
            "summary": insufficient_summary,
            "reviews": VALID_REVIEWS,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["testStatus"] == "SUCCESS"
    assert body["totalClaims"] == 0
    assert body["overallScore"] == 1.0
    assert "INSUFFICIENT_DATA" in (body.get("errorMessage") or "")


def test_failed_summary_skips_test(monkeypatch):
    """
    逻辑分支：摘要状态为 FAILED 时，不调用 LLM 直接返回。
    """
    failed_summary = dict(VALID_SUMMARY)
    failed_summary["summaryStatus"] = "FAILED"

    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json={
            "requestId": "req-test-008",
            "merchantId": 10,
            "summary": failed_summary,
            "reviews": VALID_REVIEWS,
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["totalClaims"] == 0
    assert "FAILED" in (body.get("errorMessage") or "")


# ============================================
# LLM 调用失败 → 端点级降级
# ============================================

def test_llm_failure_returns_graceful_response(monkeypatch):
    """
    降级场景：LLM 调用异常，端点返回 FAILED 状态但 HTTP 200。
    """
    _mock_llm_failure(monkeypatch)

    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json=VALID_REQUEST_BODY,
    )

    assert response.status_code == 200
    body = response.json()

    assert body["testStatus"] == "FAILED"
    assert body["overallScore"] == 0.0
    assert body["faithfulCount"] == 0
    assert body["uncertainCount"] == body["totalClaims"]
    assert body["errorMessage"] is not None
    assert "模拟 LLM 服务不可用" in body["errorMessage"]


# ============================================
# 响应 schema 完整性与字段类型
# ============================================

def test_response_matches_schema(monkeypatch):
    """
    Schema 一致性：响应的所有必填字段存在且类型正确。
    """
    _mock_llm_all_faithful(monkeypatch)

    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json=VALID_REQUEST_BODY,
    )

    assert response.status_code == 200
    body = response.json()

    # 顶层字段
    assert isinstance(body["merchantId"], int)
    assert body["testStatus"] in ("SUCCESS", "PARTIAL", "FAILED")
    assert isinstance(body["overallScore"], float)
    assert 0.0 <= body["overallScore"] <= 1.0
    assert isinstance(body["totalClaims"], int)
    assert isinstance(body["faithfulCount"], int)
    assert isinstance(body["unfaithfulCount"], int)
    assert isinstance(body["uncertainCount"], int)
    assert isinstance(body["claimResults"], list)
    assert body["faithfulCount"] + body["unfaithfulCount"] + body["uncertainCount"] == body["totalClaims"]
    assert body.get("summaryText") is not None
    assert body.get("modelName") is not None
    assert body.get("businessTraceId") is not None

    # claimResults 内部字段
    for claim in body["claimResults"]:
        assert claim["verdict"] in ("FAITHFUL", "UNFAITHFUL", "UNCERTAIN")
        assert isinstance(claim["confidence"], float)
        assert 0.0 <= claim["confidence"] <= 1.0
        assert claim["reasoning"] != ""
        assert isinstance(claim["citedReviewIds"], list)
        assert isinstance(claim["actualMatchingCount"], int)
        assert claim["actualMatchingCount"] >= 0


# ============================================
# 混合判定场景（端点级）
# ============================================

def test_partial_result_when_some_unfaithful(monkeypatch):
    """
    端点级混合判定：存在 UNFAITHFUL 声明 → testStatus="PARTIAL"。
    """
    async def fake_mixed(**_kwargs):
        return {"claimResults": [
            {"claimIndex": 0, "verdict": "FAITHFUL", "confidence": 0.95, "reasoning": "有依据"},
            {"claimIndex": 1, "verdict": "UNFAITHFUL", "confidence": 0.88, "reasoning": "评价中没有排队相关内容"},
            {"claimIndex": 2, "verdict": "FAITHFUL", "confidence": 0.90, "reasoning": "总体概括准确"},
        ]}

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_mixed)

    response = client.post(
        "/internal/reviews/summary-faithfulness-test",
        headers=_auth_headers(),
        json=VALID_REQUEST_BODY,
    )

    assert response.status_code == 200
    body = response.json()

    assert body["testStatus"] == "PARTIAL"
    assert body["faithfulCount"] == 2
    assert body["unfaithfulCount"] == 1
    assert body["overallScore"] == pytest.approx(2.0 / 3.0)
