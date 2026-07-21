"""
周边竞品对比服务单元测试（EPIC-02 Story 6）

覆盖场景：
- 正常对比分析（含本店+2家竞品）
- LLM 返回非法 merchantId 时过滤
- LLM 返回空分析时的补位
- LLM 调用失败时的降级响应
- 改进建议超3条截断
"""
from app.models.schemas import (
    CompetitorComparisonRequest,
    CompetitorMerchantData,
)
from app.services import competitor_comparison_service as service_module


# ============================================
# 测试数据构造
# ============================================

def _make_merchant(
    merchant_id: int,
    name: str,
    avg_price: float,
    rating: float,
    review_count: int,
    positive_rate: float,
) -> CompetitorMerchantData:
    """构造测试用商家数据。"""
    return CompetitorMerchantData(
        merchantId=merchant_id,
        merchantName=name,
        category="火锅",
        cuisine="川味火锅",
        address="测试地址",
        averagePrice=avg_price,
        rating=rating,
        reviewCount=review_count,
        positiveRate=positive_rate,
        tasteRating=rating + 0.1,
        environmentRating=rating - 0.1,
        serviceRating=rating,
        topPositiveTags=["口味好", "环境舒适"],
        topNegativeIssues=["排队久", "价格偏高"],
    )


def make_request() -> CompetitorComparisonRequest:
    """构造包含本店+2家竞品的标准测试请求。"""
    return CompetitorComparisonRequest(
        merchantId=1,
        competitors=[
            _make_merchant(1, "本店-川味火锅", 80.0, 4.2, 200, 0.85),
            _make_merchant(2, "竞品A-蜀都火锅", 95.0, 4.5, 350, 0.92),
            _make_merchant(3, "竞品B-老灶火锅", 65.0, 3.8, 120, 0.72),
        ],
    )


# ============================================
# 辅助方法
# ============================================

async def _run_with_llm_result(monkeypatch, result: dict):
    """用 monkeypatch 替换 LLM 返回值后执行对比。"""
    async def fake_chat_json(**_kwargs):
        return result

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json)
    return await service_module.competitor_comparison_service.compare(make_request())


# ============================================
# 测试用例
# ============================================

async def test_normal_comparison_returns_all_analyses(monkeypatch):
    """
    正常场景：LLM 返回3家商家的分析，响应包含所有字段。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {
            "merchantAnalyses": [
                {
                    "merchantId": 1,
                    "merchantName": "本店-川味火锅",
                    "strengths": ["价格实惠：人均80元低于竞品均值"],
                    "weaknesses": ["评分4.2低于竞品A的4.5"],
                    "overallAssessment": "性价比不错的中档火锅店",
                },
                {
                    "merchantId": 2,
                    "merchantName": "竞品A-蜀都火锅",
                    "strengths": ["评分最高4.5", "好评率最高92%"],
                    "weaknesses": ["价格最高95元"],
                    "overallAssessment": "品质最佳的火锅店",
                },
                {
                    "merchantId": 3,
                    "merchantName": "竞品B-老灶火锅",
                    "strengths": ["价格最低65元"],
                    "weaknesses": ["评分最低3.8", "好评率最低72%"],
                    "overallAssessment": "走低价路线的火锅店",
                },
            ],
            "summaryText": "本店在中档价位段具备一定优势，但评分和好评率与头部竞品尚有差距。",
            "improvementSuggestions": [
                "评分4.2低于竞品A的4.5，建议重点提升服务体验和菜品品质",
                "好评率85%低于竞品A的92%，建议关注差评高发问题并针对性改进",
            ],
        },
    )

    assert response.comparisonStatus == "SUCCESS"
    assert response.merchantId == 1
    assert len(response.merchantAnalyses) == 3
    assert response.summaryText is not None
    assert len(response.improvementSuggestions) == 2
    assert response.modelName is not None
    assert response.businessTraceId is not None
    assert response.errorMessage is None


async def test_llm_invalid_merchant_ids_are_filtered(monkeypatch):
    """
    安全校验：LLM 返回了不存在的 merchantId=999，应被过滤掉。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {
            "merchantAnalyses": [
                {
                    "merchantId": 1,
                    "merchantName": "本店",
                    "strengths": [],
                    "weaknesses": [],
                    "overallAssessment": "正常",
                },
                {
                    "merchantId": 999,  # 非法ID
                    "merchantName": "不存在的店",
                    "strengths": ["编造的优势"],
                    "weaknesses": [],
                    "overallAssessment": "应该被过滤",
                },
                {
                    "merchantId": 2,
                    "merchantName": "竞品A",
                    "strengths": [],
                    "weaknesses": [],
                    "overallAssessment": "正常",
                },
            ],
            "summaryText": "总结",
            "improvementSuggestions": [],
        },
    )

    valid_ids = {a.merchantId for a in response.merchantAnalyses}
    assert 999 not in valid_ids, "非法 merchantId=999 必须被过滤"
    # merchantId=2 的分析应该保留
    assert 2 in valid_ids
    # 被过滤的商家应有补位空记录（merchantId=3）
    assert 3 in valid_ids


async def test_llm_missing_merchant_gets_placeholder(monkeypatch):
    """
    补位逻辑：LLM 只返回了2家的分析（漏掉竞品B），应自动补空记录。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {
            "merchantAnalyses": [
                {
                    "merchantId": 1,
                    "strengths": ["优势"],
                    "weaknesses": [],
                    "overallAssessment": "本店分析",
                },
                {
                    "merchantId": 2,
                    "strengths": [],
                    "weaknesses": ["短板"],
                    "overallAssessment": "竞品A分析",
                },
            ],
            "summaryText": "总结",
            "improvementSuggestions": [],
        },
    )

    # 应该有3条分析（包括补位的竞品B）
    assert len(response.merchantAnalyses) == 3
    names = {a.merchantName for a in response.merchantAnalyses}
    assert "竞品B-老灶火锅" in names, "缺失的竞品B应补位"


async def test_llm_failure_returns_fallback(monkeypatch):
    """
    降级场景：LLM 调用抛出异常时，返回 FAILED 状态 + 错误信息。
    """
    async def fake_chat_json_error(**_kwargs):
        raise RuntimeError("模拟网络超时")

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json_error)

    response = await service_module.competitor_comparison_service.compare(make_request())

    assert response.comparisonStatus == "FAILED"
    assert "模拟网络超时" in (response.errorMessage or "")
    assert response.merchantAnalyses == [] or response.merchantAnalyses is not None
    assert response.businessTraceId is not None


async def test_zero_improvement_suggestions_is_valid(monkeypatch):
    """
    空建议场景：本店全面领先时 improvementSuggestions 可以为空。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {
            "merchantAnalyses": [
                {
                    "merchantId": 1,
                    "strengths": ["评分最高", "好评率最高", "价格最优"],
                    "weaknesses": [],
                    "overallAssessment": "全面领先",
                },
                {
                    "merchantId": 2,
                    "strengths": [],
                    "weaknesses": ["各项指标均不如本店"],
                    "overallAssessment": "被本店全面碾压",
                },
                {
                    "merchantId": 3,
                    "strengths": [],
                    "weaknesses": ["各项指标均不如本店"],
                    "overallAssessment": "被本店全面碾压",
                },
            ],
            "summaryText": "本店全面领先",
            "improvementSuggestions": [],
        },
    )

    assert response.comparisonStatus == "SUCCESS"
    assert len(response.improvementSuggestions) == 0


async def test_suggestions_capped_at_three(monkeypatch):
    """
    截断逻辑：LLM 返回超3条建议时截断为最多3条。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {
            "merchantAnalyses": [
                {"merchantId": 1, "strengths": [], "weaknesses": [], "overallAssessment": "ok"},
                {"merchantId": 2, "strengths": [], "weaknesses": [], "overallAssessment": "ok"},
                {"merchantId": 3, "strengths": [], "weaknesses": [], "overallAssessment": "ok"},
            ],
            "summaryText": "总结",
            "improvementSuggestions": [
                "建议1", "建议2", "建议3", "建议4", "建议5",
            ],
        },
    )

    assert len(response.improvementSuggestions) == 3, "建议最多保留3条"


async def test_no_significant_difference_handled(monkeypatch):
    """
    无明显差异场景：所有商家指标接近时，AI 应说明无明显差异而不强行编造。
    """
    response = await _run_with_llm_result(
        monkeypatch,
        {
            "merchantAnalyses": [
                {"merchantId": 1, "strengths": [], "weaknesses": [],
                 "overallAssessment": "与竞品相比差异不大，各指标均在中位水平"},
                {"merchantId": 2, "strengths": [], "weaknesses": [],
                 "overallAssessment": "与本店指标接近，无明显优势或短板"},
                {"merchantId": 3, "strengths": [], "weaknesses": [],
                 "overallAssessment": "与同区域竞品整体水平相当"},
            ],
            "summaryText": "各商家在核心指标上差异不大，无明显领先者。",
            "improvementSuggestions": [
                "建议通过差异化服务（如特色菜品、会员体系）建立竞争优势",
            ],
        },
    )

    assert response.comparisonStatus == "SUCCESS"
    assert "差异不大" in response.summaryText
    # strengths 和 weaknesses 可以为空（无明显差异）
    for a in response.merchantAnalyses:
        assert isinstance(a.strengths, list)
        assert isinstance(a.weaknesses, list)


async def test_request_with_4_merchants_is_valid(monkeypatch):
    """
    边界场景：本店+3家竞品（共4家，上限）是合法的。
    """
    request = CompetitorComparisonRequest(
        merchantId=1,
        competitors=[
            _make_merchant(1, "本店", 80, 4.0, 100, 0.80),
            _make_merchant(2, "竞品A", 90, 4.1, 200, 0.82),
            _make_merchant(3, "竞品B", 70, 3.9, 150, 0.78),
            _make_merchant(4, "竞品C", 85, 4.3, 300, 0.88),
        ],
    )

    async def fake_chat_json(**_kwargs):
        return {
            "merchantAnalyses": [
                {"merchantId": i, "strengths": [], "weaknesses": [],
                 "overallAssessment": "ok"} for i in [1, 2, 3, 4]
            ],
            "summaryText": "测试总结",
            "improvementSuggestions": [],
        }

    monkeypatch.setattr(service_module.llm_service, "chat_json", fake_chat_json)
    response = await service_module.competitor_comparison_service.compare(request)

    assert response.comparisonStatus == "SUCCESS"
    assert len(response.merchantAnalyses) == 4
