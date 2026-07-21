"""
评论摘要忠实性测试 — 单元测试（EPIC-06 Story 3）

覆盖所有核心路径 + 异常分支：
  正常场景:
  - 全部声明判定为 FAITHFUL（满分）
  - 混合判定（FAITHFUL / UNFAITHFUL / UNCERTAIN 并存）
  - 全部声明不忠实
  - 多种声明类型混合（advantage + disadvantage + dish + environment + service）

  异常/边界场景:
  - LLM 调用失败 → 降级，所有声明 UNCERTAIN
  - LLM 返回的 claimIndex 部分缺失 → 缺失项补 UNCERTAIN
  - LLM 返回无效 verdict → 容错为 UNCERTAIN
  - LLM 返回的 confidence 非法值 → 钳制到 [0,1]
  - 声明无有效匹配评价 → 过滤跳过
  - 所有声明都被过滤 → 返回空结果
  - 评价原文过长 → 截断
  - recentChange 方向拼入声明文本
  - summaryText 类型声明验证
"""
import pytest
from unittest.mock import AsyncMock, patch, MagicMock

from app.models.schemas import (
    FaithfulnessTestRequest,
    FaithfulnessTestResponse,
    FaithfulnessClaimResult,
    FaithfulnessVerdictEnum,
    FaithfulnessReviewItem,
    ReviewSummaryResponse,
    SummaryPoint,
)
from app.services import faithfulness_test_service as service_module


# ============================================
# 测试数据构造工厂
# ============================================

def _make_review(review_id: int, content: str, rating: int = 4) -> FaithfulnessReviewItem:
    """构造测试用单条评价。"""
    return FaithfulnessReviewItem(
        reviewId=review_id,
        content=content,
        rating=rating,
    )


def _make_summary(
    merchant_id: int = 10,
    *,
    summary_text: str = "总体口碑良好",
    advantages: list | None = None,
    disadvantages: list | None = None,
    dishes: list | None = None,
    environment: dict | None = None,
    service: dict | None = None,
    recent_changes: list | None = None,
    summary_status: str = "SUCCESS",
) -> ReviewSummaryResponse:
    """
    构造测试用摘要响应。

    默认携带一个 advantage 和一个 disadvantage，可通过参数覆盖。
    """
    return ReviewSummaryResponse(
        merchantId=merchant_id,
        version=1,
        summaryStatus=summary_status,
        summaryText=summary_text,
        advantages=advantages if advantages is not None else [
            SummaryPoint(name="菜品口味好", mentionCount=2, reviewIds=[101, 102]),
        ],
        disadvantages=disadvantages if disadvantages is not None else [
            SummaryPoint(name="排队时间久", mentionCount=1, reviewIds=[103]),
        ],
        recommendedDishes=dishes if dishes is not None else [],
        environmentSummary=environment or {},
        serviceSummary=service or {},
        recentChanges=recent_changes or [],
        reviewCount=3,
        minimumReviewCount=3,
        evidences=[],
        modelName="test-model",
    )


def _make_request(
    merchant_id: int = 10,
    summary: ReviewSummaryResponse | None = None,
    reviews: list | None = None,
) -> FaithfulnessTestRequest:
    """构造测试用忠实性测试请求。"""
    return FaithfulnessTestRequest(
        requestId="req-test-001",
        merchantId=merchant_id,
        summary=summary or _make_summary(merchant_id),
        reviews=reviews or [
            _make_review(101, "红烧肉特别入味，每次来必点"),
            _make_review(102, "口味确实不错，推荐"),
            _make_review(103, "周末去排了四十分钟才吃上"),
        ],
    )


# ============================================
# 辅助方法：用 monkeypatch 替换 LLM 返回
# ============================================

async def _run_with_llm_result(monkeypatch, result: dict, request=None):
    """注入假 LLM 返回值后执行忠实性测试。"""
    async def fake_chat_json(**_kwargs):
        return result

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json)
    req = request or _make_request()
    return await service_module.faithfulness_test_service.test(req)


def _make_judge_result(*verdicts) -> dict:
    """
    快捷构造 LLM 评判返回的 claimResults。

    每个元素为 (verdict, confidence, reasoning) 三元组。
    """
    results = []
    for i, v in enumerate(verdicts):
        verdict, confidence, reasoning = v
        results.append({
            "claimIndex": i,
            "verdict": verdict,
            "confidence": confidence,
            "reasoning": reasoning,
        })
    return {"claimResults": results}


# ============================================
# 正常场景
# ============================================

async def test_all_faithful_returns_perfect_score(monkeypatch):
    """
    正常场景：所有声明都被判定为 FAITHFUL，overallScore = 1.0。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.95, "评价[reviewId=101]中提到'红烧肉特别入味'，评价[reviewId=102]中提到'口味确实不错'。声明忠实。"),
            ("FAITHFUL", 0.90, "评价[reviewId=103]中提到'排了四十分钟'。声明忠实。"),
            ("FAITHFUL", 0.88, "所有评价整体正面。声明忠实。"),
        ),
    )

    assert response.testStatus == "SUCCESS"
    assert response.overallScore == 1.0
    assert response.totalClaims > 0
    assert response.faithfulCount == response.totalClaims
    assert response.unfaithfulCount == 0
    assert response.uncertainCount == 0
    assert response.modelName is not None
    assert response.businessTraceId is not None
    assert response.summaryText == "总体口碑良好"


async def test_mixed_verdicts_compute_correct_score(monkeypatch):
    """
    混合场景：FAITHFUL + UNFAITHFUL + UNCERTAIN 并存，验证得分计算正确。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.95, "依据充分"),
            ("UNFAITHFUL", 0.92, "评价中没有提到排队相关内容"),
            ("UNCERTAIN", 0.50, "只有一条评价，不足以确认趋势"),
        ),
    )

    assert response.testStatus == "PARTIAL"  # 存在 UNFAITHFUL → PARTIAL
    assert response.faithfulCount == 1
    assert response.unfaithfulCount == 1
    assert response.uncertainCount == 1
    assert response.totalClaims == 3
    assert response.overallScore == pytest.approx(1.0 / 3.0)


async def test_all_unfaithful_returns_zero_score(monkeypatch):
    """
    异常场景：所有声明都不忠实，overallScore = 0.0。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("UNFAITHFUL", 0.95, "编造的内容"),
            ("UNFAITHFUL", 0.85, "与原文矛盾"),
            ("UNFAITHFUL", 0.90, "找不到依据"),
        ),
    )

    assert response.testStatus == "PARTIAL"
    assert response.overallScore == 0.0
    assert response.faithfulCount == 0
    assert response.unfaithfulCount == 3


async def test_multiple_claim_types(monkeypatch):
    """
    多声明类型混合：advantage + disadvantage + recommendedDish
    + environmentSummary + serviceSummary + recentChange + summaryText。
    """
    request = _make_request(
        summary=_make_summary(
            10,
            advantages=[
                SummaryPoint(name="口味好", mentionCount=2, reviewIds=[101, 102]),
            ],
            disadvantages=[
                SummaryPoint(name="分量少", mentionCount=1, reviewIds=[103]),
            ],
            dishes=[
                SummaryPoint(name="红烧肉", mentionCount=2, reviewIds=[101, 102]),
            ],
            environment={"text": "环境安静", "reviewIds": [101]},
            service={"text": "服务周到", "reviewIds": [102]},
            recent_changes=[
                {"text": "近期口味下滑", "direction": "DECLINING", "reviewIds": [103]},
            ],
        ),
    )

    response = await _run_with_llm_result(
        monkeypatch,
        # advantage(0), disadvantage(1), dish(2), environment(3), service(4), recentChange(5), summaryText(6) = 7 claims
        _make_judge_result(
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
        ),
        request=request,
    )

    assert response.totalClaims == 7
    assert response.overallScore == 1.0

    # 验证每种类型都出现了
    types = {c.claimType for c in response.claimResults}
    assert "advantage" in types
    assert "disadvantage" in types
    assert "recommendedDish" in types
    assert "environmentSummary" in types
    assert "serviceSummary" in types
    assert "recentChange" in types
    assert "summaryText" in types


# ============================================
# 异常 / 降级场景
# ============================================

async def test_llm_failure_returns_all_uncertain(monkeypatch):
    """
    降级场景：LLM 调用抛出异常，所有声明标记为 UNCERTAIN。
    """
    async def fake_chat_json_error(**_kwargs):
        raise RuntimeError("模拟 LLM 服务超时")

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json_error)

    response = await service_module.faithfulness_test_service.test(_make_request())

    assert response.testStatus == "FAILED"
    assert response.overallScore == 0.0
    assert response.faithfulCount == 0
    assert response.unfaithfulCount == 0
    assert response.uncertainCount == response.totalClaims
    assert response.totalClaims > 0
    assert response.errorMessage is not None
    assert "模拟 LLM 服务超时" in response.errorMessage
    # 每个声明都应该是 UNCERTAIN
    for c in response.claimResults:
        assert c.verdict == FaithfulnessVerdictEnum.UNCERTAIN
        assert "LLM 调用失败" in c.reasoning


async def test_empty_claims_returns_success_with_zero(monkeypatch):
    """
    边界场景：摘要中没有任何有效声明（所有字段为空），返回空结果。
    """
    empty_summary = ReviewSummaryResponse(
        merchantId=10,
        version=1,
        summaryStatus="SUCCESS",
        summaryText=None,
        advantages=[],
        disadvantages=[],
        recommendedDishes=[],
        environmentSummary={},
        serviceSummary={},
        recentChanges=[],
        reviewCount=3,
        minimumReviewCount=3,
        evidences=[],
    )

    request = _make_request(summary=empty_summary, reviews=[
        _make_review(101, "test"),
    ])

    # 即使 LLM 可用，也不应该被调用
    response = await service_module.faithfulness_test_service.test(request)

    assert response.testStatus == "SUCCESS"
    assert response.overallScore == 1.0
    assert response.totalClaims == 0
    assert response.claimResults == []


async def test_claims_with_no_matching_reviews_are_filtered(monkeypatch):
    """
    过滤场景：声明的 reviewIds 在 reviews 列表中找不到任何匹配，
    这些声明应被过滤掉（actualMatchingCount=0 → 跳过）。
    """
    summary = _make_summary(
        10,
        summary_text=None,  # 必须关闭 summaryText，否则它引用全部输入review → 有匹配
        advantages=[
            SummaryPoint(name="不存在评价支撑的优点", mentionCount=1, reviewIds=[999]),
        ],
        disadvantages=[
            SummaryPoint(name="也不存在的不足", mentionCount=1, reviewIds=[888]),
        ],
    )
    request = _make_request(summary=summary)

    # LLM 不应该被调用（无有效声明），直接验证不 mock
    response = await service_module.faithfulness_test_service.test(request)

    assert response.totalClaims == 0
    assert response.overallScore == 1.0


# ============================================
# LLM 返回结果对齐 / 容错
# ============================================

async def test_llm_missing_some_claim_indices(monkeypatch):
    """
    容错场景：LLM 返回的评判结果缺少某些 claimIndex，
    缺失项应自动补为 UNCERTAIN。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        # 只有 claimIndex=0 和 2，缺少 1 → 3 个声明
        {"claimResults": [
            {"claimIndex": 0, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "ok"},
            {"claimIndex": 2, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "ok"},
        ]},
    )

    assert response.totalClaims == 3
    assert response.claimResults[0].verdict == FaithfulnessVerdictEnum.FAITHFUL
    assert response.claimResults[1].verdict == FaithfulnessVerdictEnum.UNCERTAIN  # 缺失补位
    assert response.claimResults[1].reasoning == "评判模型未返回该声明的判定结果"
    assert response.claimResults[2].verdict == FaithfulnessVerdictEnum.FAITHFUL
    assert response.uncertainCount == 1


async def test_llm_extra_claim_indices_are_ignored(monkeypatch):
    """
    容错场景：LLM 返回了多余的 claimIndex（超出声明范围），忽略。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {"claimResults": [
            {"claimIndex": 0, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "ok"},
            {"claimIndex": 1, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "ok"},
            {"claimIndex": 2, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "ok"},
            {"claimIndex": 99, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "多余的"},
        ]},
    )

    assert response.totalClaims == 3  # 不是 4
    assert response.overallScore == 1.0


async def test_llm_invalid_verdict_defaults_to_uncertain(monkeypatch):
    """
    容错场景：LLM 返回了不在枚举范围内的 verdict，默认 UNCERTAIN。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("INVALID_VERDICT", 0.5, "模型乱输出的判定"),
            ("FAITHFUL", 0.9, "正常"),
            ("FAITHFUL", 0.9, "正常"),
        ),
    )

    assert response.claimResults[0].verdict == FaithfulnessVerdictEnum.UNCERTAIN
    assert response.claimResults[1].verdict == FaithfulnessVerdictEnum.FAITHFUL
    assert response.uncertainCount == 1


async def test_confidence_clamped_to_range(monkeypatch):
    """
    边界场景：LLM 返回的 confidence 超出 [0,1]，应被钳制。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 1.5, "confidence > 1 → 钳制为 1.0"),    # 超上限
            ("FAITHFUL", -0.3, "confidence < 0 → 钳制为 0.0"),    # 超下限
            ("FAITHFUL", "not_a_number", "非数字 → 默认 0.5"),    # 非数字
        ),
    )

    assert response.claimResults[0].confidence == 1.0
    assert response.claimResults[1].confidence == 0.0
    assert response.claimResults[2].confidence == 0.5


async def test_empty_reasoning_gets_placeholder(monkeypatch):
    """
    容错场景：LLM 返回空 reasoning，补充占位文本。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.9, ""),
            ("FAITHFUL", 0.9, "正常"),
            ("FAITHFUL", 0.9, ""),
        ),
    )

    assert response.claimResults[0].reasoning == "评判模型未提供理由"
    assert response.claimResults[1].reasoning == "正常"


# ============================================
# 特定声明类型
# ============================================

async def test_recent_change_includes_direction_in_claim_text(monkeypatch):
    """
    recentChange 类型：方向信息应拼入声明文本中。
    """
    summary = _make_summary(
        10,
        advantages=[],
        disadvantages=[],
        recent_changes=[
            {"text": "近期上菜速度下降", "direction": "DECLINING", "reviewIds": [101]},
        ],
    )
    request = _make_request(summary=summary)

    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.9, "ok"),  # summaryText
            ("FAITHFUL", 0.9, "ok"),  # recentChange
        ),
        request=request,
    )

    # 检查 recentChange 声明文本包含方向
    rc_claims = [c for c in response.claimResults if c.claimType == "recentChange"]
    assert len(rc_claims) == 1
    assert "DECLINING" in rc_claims[0].claimText


async def test_environment_summary_extracted(monkeypatch):
    """
    environmentSummary 验证：有内容时提取为声明。
    """
    summary = _make_summary(
        10,
        advantages=[],
        disadvantages=[],
        environment={"text": "环境安静适合聚餐", "reviewIds": [101, 102]},
    )
    request = _make_request(summary=summary)

    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.9, "ok"),  # environmentSummary
            ("FAITHFUL", 0.9, "ok"),  # summaryText
        ),
        request=request,
    )

    env_claims = [c for c in response.claimResults if c.claimType == "environmentSummary"]
    assert len(env_claims) == 1
    assert env_claims[0].claimText == "环境安静适合聚餐"
    assert env_claims[0].citedReviewIds == [101, 102]


async def test_service_summary_extracted(monkeypatch):
    """
    serviceSummary 验证：有内容时提取为声明。
    """
    summary = _make_summary(
        10,
        advantages=[],
        disadvantages=[],
        service={"text": "服务响应快", "reviewIds": [101]},
    )
    request = _make_request(summary=summary)

    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.9, "ok"),  # serviceSummary
            ("FAITHFUL", 0.9, "ok"),  # summaryText
        ),
        request=request,
    )

    svc_claims = [c for c in response.claimResults if c.claimType == "serviceSummary"]
    assert len(svc_claims) == 1
    assert svc_claims[0].claimText == "服务响应快"


async def test_summary_text_uses_all_review_ids(monkeypatch):
    """
    summaryText 类型：应引用 reviews 列表中所有的 reviewId。
    """
    request = _make_request()  # 3 reviews: 101, 102, 103 → summaryText 应引用全部

    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.9, "ok"),  # advantage
            ("FAITHFUL", 0.9, "ok"),  # disadvantage
            ("FAITHFUL", 0.9, "ok"),  # summaryText
        ),
        request=request,
    )

    st_claims = [c for c in response.claimResults if c.claimType == "summaryText"]
    assert len(st_claims) == 1
    assert set(st_claims[0].citedReviewIds) == {101, 102, 103}


async def test_null_summary_text_not_extracted(monkeypatch):
    """
    summaryText 为 None 时不提取声明。
    """
    summary = _make_summary(10, summary_text=None)
    request = _make_request(summary=summary)

    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
        ),
        request=request,
    )

    st_claims = [c for c in response.claimResults if c.claimType == "summaryText"]
    assert len(st_claims) == 0


# ============================================
# actualMatchingCount 准确性
# ============================================

async def test_actual_matching_count_partial_match(monkeypatch):
    """
    边界场景：reviewIds 中部分能匹配到原文，部分不能。
    actualMatchingCount 应只计算能匹配的。
    """
    summary = _make_summary(
        10,
        advantages=[
            SummaryPoint(name="部分匹配", mentionCount=3, reviewIds=[101, 999, 888]),
        ],
        disadvantages=[],
    )

    request = _make_request(
        summary=summary,
        reviews=[
            _make_review(101, "红烧肉好吃"),
            # 999 和 888 不在 reviews 中
        ],
    )

    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),  # summaryText
        ),
        request=request,
    )

    adv_claims = [c for c in response.claimResults if c.claimType == "advantage"]
    assert len(adv_claims) == 1
    assert adv_claims[0].actualMatchingCount == 1  # 只有 101 匹配
    assert adv_claims[0].citedReviewIds == [101, 999, 888]


# ============================================
# 长文本截断
# ============================================

async def test_long_review_content_is_truncated(monkeypatch):
    """
    边界场景：评价原文超过 500 字时被截断，不影响评判流程。
    """
    long_content = "好吃" * 300  # 600 字，超限
    request = _make_request(
        reviews=[
            _make_review(101, long_content),
            _make_review(102, "正常评价"),
            _make_review(103, "测试内容"),
        ],
    )

    response = await _run_with_llm_result(
        monkeypatch,
        _make_judge_result(
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
            ("FAITHFUL", 0.9, "ok"),
        ),
        request=request,
    )

    assert response.testStatus == "SUCCESS"
    assert response.overallScore == 1.0


# ============================================
# LLM 返回非标准 JSON 结构
# ============================================

async def test_llm_returns_empty_claim_results(monkeypatch):
    """
    容错场景：LLM 返回空 claimResults 数组，全部标记 UNCERTAIN。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {"claimResults": []},
    )

    assert response.uncertainCount == response.totalClaims
    for c in response.claimResults:
        assert c.verdict == FaithfulnessVerdictEnum.UNCERTAIN


async def test_llm_returns_non_dict_items_in_claim_results(monkeypatch):
    """
    容错场景：LLM 在 claimResults 中掺杂非 dict 元素，跳过。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {"claimResults": [
            {"claimIndex": 0, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "ok"},
            "not_a_dict",  # 应被跳过
            {"claimIndex": 2, "verdict": "FAITHFUL", "confidence": 0.9, "reasoning": "ok"},
        ]},
    )

    assert response.claimResults[0].verdict == FaithfulnessVerdictEnum.FAITHFUL
    assert response.claimResults[2].verdict == FaithfulnessVerdictEnum.FAITHFUL
    # claimResults[1] 应为补位的 UNCERTAIN
    assert response.claimResults[1].verdict == FaithfulnessVerdictEnum.UNCERTAIN
