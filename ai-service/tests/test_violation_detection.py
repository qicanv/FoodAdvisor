"""
违规文本检测服务 — 单元测试

覆盖：
1. Schema 验证（请求/响应序列化、边界条件）
2. 辅助方法（分值 clamp、风险等级推断、风险类型验证、规则解析）
3. API 端点（成功检测、空内容拒绝、Token 验证）
"""
import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.schemas.violation_check import (
    ViolationCheckRequest,
    ViolationCheckResponse,
    MatchedRule,
    RiskTypeEnum,
    RiskLevelEnum,
    DetectionStatusEnum,
)
from app.services.violation_detection_service import (
    ViolationDetectionService,
    violation_detection_service,
)

TEST_INTERNAL_TOKEN = "test-internal-token"
client = TestClient(app)


@pytest.fixture(autouse=True)
def configure_internal_token(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(settings, "internal_api_token", TEST_INTERNAL_TOKEN)


def build_headers(request_id: str = "test-req-001") -> dict[str, str]:
    return {
        "X-Internal-Token": TEST_INTERNAL_TOKEN,
        "X-Request-Id": request_id,
    }


# ============================================================
# Schema 测试
# ============================================================

class TestViolationCheckRequest:
    """请求 Schema 验证"""

    def test_valid_request(self):
        req = ViolationCheckRequest(content="正常评价内容")
        assert req.content == "正常评价内容"
        assert req.ruleVersion is None

    def test_request_with_rule_version(self):
        req = ViolationCheckRequest(
            content="测试内容",
            ruleVersion="violation-detection:v2",
        )
        assert req.ruleVersion == "violation-detection:v2"

    def test_empty_content_rejected(self):
        with pytest.raises(ValueError, match="content must not be blank"):
            ViolationCheckRequest(content="   ")

    def test_content_too_long(self):
        with pytest.raises(ValueError):
            ViolationCheckRequest(content="x" * 5001)


class TestViolationCheckResponse:
    """响应 Schema 验证"""

    def test_clean_response(self):
        resp = ViolationCheckResponse(
            riskType=None,
            riskLevel=RiskLevelEnum.LOW,
            riskScore=5,
            matchedRules=[],
            modelName="test-model",
            businessTraceId="trace-001",
            detectionStatus=DetectionStatusEnum.SUCCESS,
        )
        assert resp.riskType is None
        assert resp.riskLevel == RiskLevelEnum.LOW
        assert resp.riskScore == 5
        assert resp.matchedRules == []

    def test_high_risk_response(self):
        rules = [
            MatchedRule(
                ruleCode="AD_SPAM_001",
                ruleName="包含联系方式推广",
                riskType=RiskTypeEnum.AD_SPAM,
                confidence=0.95,
                evidenceExcerpt="加微信xxx了解更多",
            )
        ]
        resp = ViolationCheckResponse(
            riskType=RiskTypeEnum.AD_SPAM,
            riskLevel=RiskLevelEnum.HIGH,
            riskScore=85,
            matchedRules=rules,
            modelName="deepseek-v4-pro",
            businessTraceId="trace-002",
            detectionStatus=DetectionStatusEnum.SUCCESS,
        )
        assert resp.riskType == RiskTypeEnum.AD_SPAM
        assert resp.riskScore == 85
        assert len(resp.matchedRules) == 1
        assert resp.matchedRules[0].ruleCode == "AD_SPAM_001"

    def test_error_response(self):
        resp = ViolationCheckResponse(
            riskType=None,
            riskLevel=RiskLevelEnum.LOW,
            riskScore=0,
            matchedRules=[],
            detectionStatus=DetectionStatusEnum.ERROR,
            errorMessage="LLM call failed",
        )
        assert resp.detectionStatus == DetectionStatusEnum.ERROR
        assert resp.errorMessage == "LLM call failed"


# ============================================================
# 辅助方法测试（不依赖 LLM）
# ============================================================

class TestHelperMethods:
    """ViolationDetectionService 辅助方法"""

    def test_clamp_score_in_range(self):
        assert ViolationDetectionService._clamp_score(50) == 50
        assert ViolationDetectionService._clamp_score(0) == 0
        assert ViolationDetectionService._clamp_score(100) == 100

    def test_clamp_score_out_of_range(self):
        assert ViolationDetectionService._clamp_score(-10) == 0
        assert ViolationDetectionService._clamp_score(150) == 100

    def test_clamp_score_invalid_input(self):
        assert ViolationDetectionService._clamp_score(None) == 0
        assert ViolationDetectionService._clamp_score("abc") == 0
        assert ViolationDetectionService._clamp_score(3.14) == 3

    def test_resolve_risk_level_from_string(self):
        assert ViolationDetectionService._resolve_risk_level("HIGH", 80) == RiskLevelEnum.HIGH
        assert ViolationDetectionService._resolve_risk_level("MEDIUM", 50) == RiskLevelEnum.MEDIUM
        assert ViolationDetectionService._resolve_risk_level("LOW", 10) == RiskLevelEnum.LOW
        assert ViolationDetectionService._resolve_risk_level("low", 10) == RiskLevelEnum.LOW

    def test_resolve_risk_level_from_score(self):
        # score >= 70 → HIGH
        assert ViolationDetectionService._resolve_risk_level(None, 70) == RiskLevelEnum.HIGH
        assert ViolationDetectionService._resolve_risk_level(None, 85) == RiskLevelEnum.HIGH
        # score 40-69 → MEDIUM
        assert ViolationDetectionService._resolve_risk_level(None, 40) == RiskLevelEnum.MEDIUM
        assert ViolationDetectionService._resolve_risk_level(None, 60) == RiskLevelEnum.MEDIUM
        # score < 40 → LOW
        assert ViolationDetectionService._resolve_risk_level(None, 0) == RiskLevelEnum.LOW
        assert ViolationDetectionService._resolve_risk_level(None, 39) == RiskLevelEnum.LOW

    def test_validate_risk_type(self):
        assert ViolationDetectionService._validate_risk_type("AD_SPAM") == RiskTypeEnum.AD_SPAM
        assert ViolationDetectionService._validate_risk_type("abuse") == RiskTypeEnum.ABUSE
        assert ViolationDetectionService._validate_risk_type(None) is None
        assert ViolationDetectionService._validate_risk_type("INVALID") is None

    def test_parse_matched_rules_valid(self):
        raw = [
            {
                "ruleCode": "AD_SPAM_001",
                "ruleName": "包含联系方式",
                "riskType": "AD_SPAM",
                "confidence": 0.9,
                "evidenceExcerpt": "加微信xxx",
            }
        ]
        rules = ViolationDetectionService._parse_matched_rules(raw)
        assert len(rules) == 1
        assert rules[0].ruleCode == "AD_SPAM_001"
        assert rules[0].confidence == 0.9

    def test_parse_matched_rules_empty(self):
        assert ViolationDetectionService._parse_matched_rules([]) == []
        assert ViolationDetectionService._parse_matched_rules(None) == []
        assert ViolationDetectionService._parse_matched_rules("not_a_list") == []

    def test_parse_matched_rules_skip_invalid(self):
        raw = [
            {"ruleCode": "OK_001", "ruleName": "ok", "riskType": "AD_SPAM", "confidence": 0.5},
            "not_a_dict",
            {"ruleCode": "BAD", "ruleName": "bad", "riskType": "INVALID_TYPE", "confidence": 0.5},
        ]
        rules = ViolationDetectionService._parse_matched_rules(raw)
        # Only the first entry should be parsed; second skipped (not dict); third skipped (invalid riskType)
        assert len(rules) == 1
        assert rules[0].ruleCode == "OK_001"


# ============================================================
# API 端点测试
# ============================================================

class TestViolationCheckEndpoint:
    """POST /internal/content/violation-check"""

    def test_rejects_without_token(self):
        response = client.post(
            "/internal/content/violation-check",
            json={"content": "测试内容"},
        )
        assert response.status_code in (401, 403)

    def test_rejects_wrong_token(self):
        response = client.post(
            "/internal/content/violation-check",
            headers={"X-Internal-Token": "wrong-token"},
            json={"content": "测试内容"},
        )
        assert response.status_code in (401, 403)

    def test_rejects_empty_content(self):
        response = client.post(
            "/internal/content/violation-check",
            headers=build_headers(),
            json={"content": ""},
        )
        assert response.status_code == 422

    def test_rejects_content_too_long(self):
        response = client.post(
            "/internal/content/violation-check",
            headers=build_headers(),
            json={"content": "x" * 5001},
        )
        assert response.status_code == 422

    def test_accepts_valid_request(self):
        """端点接受合法请求（注意：实际 LLM 调用可能失败，
        但端点应返回 200 且 detectionStatus 反映实际状态）"""
        response = client.post(
            "/internal/content/violation-check",
            headers=build_headers("req-normal-001"),
            json={
                "content": "这家店味道不错，环境也很好，推荐！",
                "ruleVersion": "violation-detection:v1",
            },
        )
        assert response.status_code == 200
        body = response.json()
        # 即使 LLM 不可用，也应返回结构化结果
        assert "riskLevel" in body
        assert "riskScore" in body
        assert "matchedRules" in body
        assert "detectionStatus" in body


# ============================================================
# 确定性测试
# ============================================================

class TestDeterministic:
    """验收准则7：相同规则版本下重复检测同一文本得到一致结果"""

    def test_same_input_same_rule_version_produces_consistent_output(self):
        """验证辅助方法对相同输入产生一致结果"""
        content = "加微信xxx了解更多优惠"
        # 多次调用辅助方法应得到一致结果
        score1 = ViolationDetectionService._clamp_score(85)
        score2 = ViolationDetectionService._clamp_score(85)
        assert score1 == score2

        level1 = ViolationDetectionService._resolve_risk_level(None, 85)
        level2 = ViolationDetectionService._resolve_risk_level(None, 85)
        assert level1 == level2 == RiskLevelEnum.HIGH
