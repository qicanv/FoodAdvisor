package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.violation.ViolationTextResult;
import com.foodadvisor.entity.ContentRiskRecord;
import com.foodadvisor.mapper.ContentRiskRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ViolationTextService 单元测试
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>AI 检测成功 — 各风险等级解析</li>
 *   <li>AI 检测失败 — 降级到关键词匹配</li>
 *   <li>空内容处理</li>
 *   <li>检测记录保存</li>
 *   <li>命中规则解析</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ViolationTextServiceTest {

    @Mock
    private AIClientService aiClientService;

    @Mock
    private ContentRiskRecordMapper riskRecordMapper;

    private ViolationTextService violationTextService;
    private ObjectMapper objectMapper;

    private static final String RULE_VERSION = "violation-detection:v1";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        violationTextService = new ViolationTextService(
                aiClientService,
                riskRecordMapper,
                objectMapper
        );
    }

    // ==================== AI 检测成功场景 ====================

    @Test
    void shouldReturnLowRiskForNormalContent() throws Exception {
        JsonNode aiResponse = buildAiResponse(null, "LOW", 10, List.of());
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenReturn(aiResponse);

        ViolationTextResult result = violationTextService.detectViolation(
                "这家店味道不错，环境也很好", RULE_VERSION);

        assertAll(
                () -> assertFalse(result.isViolation()),
                () -> assertEquals("LOW", result.getRiskLevel()),
                () -> assertEquals(10, result.getRiskScore()),
                () -> assertNull(result.getRiskType()),
                () -> assertTrue(result.getMatchedRules().isEmpty()),
                () -> assertEquals("SUCCESS", result.getDetectionStatus())
        );
    }

    @Test
    void shouldReturnMediumRiskForUncertainContent() throws Exception {
        JsonNode aiResponse = buildAiResponse("SPAM", "MEDIUM", 55, List.of());
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenReturn(aiResponse);

        ViolationTextResult result = violationTextService.detectViolation(
                "啊啊啊啊啊好好吃", RULE_VERSION);

        assertAll(
                () -> assertTrue(result.isViolation()),
                () -> assertEquals("MEDIUM", result.getRiskLevel()),
                () -> assertEquals(55, result.getRiskScore())
        );
    }

    @Test
    void shouldReturnHighRiskForAdSpam() throws Exception {
        List<ViolationTextResult.MatchedRuleInfo> rules = List.of(
                ViolationTextResult.MatchedRuleInfo.builder()
                        .ruleCode("AD_SPAM_001")
                        .ruleName("包含联系方式推广")
                        .riskType("AD_SPAM")
                        .confidence(0.95)
                        .evidenceExcerpt("加微信xxx了解更多")
                        .build()
        );
        JsonNode aiResponse = buildAiResponse("AD_SPAM", "HIGH", 85, rules);
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenReturn(aiResponse);

        ViolationTextResult result = violationTextService.detectViolation(
                "加微信xxx了解更多优惠活动，扫码进群送福利", RULE_VERSION);

        assertAll(
                () -> assertTrue(result.isViolation()),
                () -> assertEquals("HIGH", result.getRiskLevel()),
                () -> assertEquals(85, result.getRiskScore()),
                () -> assertEquals("AD_SPAM", result.getRiskType()),
                () -> assertEquals(1, result.getMatchedRules().size()),
                () -> assertEquals("AD_SPAM_001", result.getMatchedRules().get(0).getRuleCode()),
                () -> assertEquals(0.95, result.getMatchedRules().get(0).getConfidence(), 0.001)
        );
    }

    @Test
    void shouldReturnHighRiskForAbuse() throws Exception {
        List<ViolationTextResult.MatchedRuleInfo> rules = List.of(
                ViolationTextResult.MatchedRuleInfo.builder()
                        .ruleCode("ABUSE_001")
                        .ruleName("人身攻击")
                        .riskType("ABUSE")
                        .confidence(0.92)
                        .evidenceExcerpt("老板是个傻X")
                        .build()
        );
        JsonNode aiResponse = buildAiResponse("ABUSE", "HIGH", 82, rules);
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenReturn(aiResponse);

        ViolationTextResult result = violationTextService.detectViolation(
                "老板是个傻X，服务太差了", RULE_VERSION);

        assertAll(
                () -> assertTrue(result.isViolation()),
                () -> assertEquals("HIGH", result.getRiskLevel()),
                () -> assertEquals("ABUSE", result.getRiskType())
        );
    }

    @Test
    void shouldCorrectRiskLevelBasedOnScore() throws Exception {
        // AI returns LOW but score is 75 → should be corrected to HIGH
        JsonNode aiResponse = buildAiResponse("AD_SPAM", "LOW", 75, List.of());
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenReturn(aiResponse);

        ViolationTextResult result = violationTextService.detectViolation(
                "广告内容", RULE_VERSION);

        assertAll(
                () -> assertTrue(result.isViolation()),
                // parseAiResponse corrects LOW to HIGH when score >= 70
                () -> assertEquals("HIGH", result.getRiskLevel())
        );
    }

    // ==================== 降级场景 ====================

    @Test
    void shouldFallbackToKeywordMatchOnAiFailure() {
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        ViolationTextResult result = violationTextService.detectViolation(
                "正常评价内容", RULE_VERSION);

        assertAll(
                () -> assertFalse(result.isViolation()),
                () -> assertEquals("LOW", result.getRiskLevel()),
                () -> assertEquals("FALLBACK", result.getDetectionStatus()),
                () -> assertEquals("keyword-fallback", result.getModelName())
        );
    }

    @Test
    void shouldDetectSensitiveKeywordInFallback() {
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        ViolationTextResult result = violationTextService.detectViolation(
                "这个涉及暴恐内容", RULE_VERSION);

        assertAll(
                () -> assertTrue(result.isViolation()),
                () -> assertEquals("HIGH", result.getRiskLevel()),
                () -> assertEquals(80, result.getRiskScore()),
                () -> assertEquals("FALLBACK", result.getDetectionStatus())
        );
    }

    @Test
    void shouldDetectMultipleSensitiveKeywordsInFallback() {
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenThrow(new RuntimeException("Timeout"));

        ViolationTextResult result = violationTextService.detectViolation(
                "涉及色情和赌博的内容", RULE_VERSION);

        assertAll(
                () -> assertTrue(result.isViolation()),
                () -> assertEquals("FALLBACK", result.getDetectionStatus())
        );
    }

    // ==================== 空内容/边界场景 ====================

    @Test
    void shouldReturnErrorForBlankContent() {
        ViolationTextResult result = violationTextService.detectViolation(
                "   ", RULE_VERSION);

        assertAll(
                () -> assertFalse(result.isViolation()),
                () -> assertEquals("ERROR", result.getDetectionStatus()),
                () -> assertEquals("LOW", result.getRiskLevel())
        );
        verify(aiClientService, never()).checkViolationText(anyString(), anyString());
    }

    @Test
    void shouldReturnErrorForNullContent() {
        ViolationTextResult result = violationTextService.detectViolation(
                null, RULE_VERSION);

        assertAll(
                () -> assertFalse(result.isViolation()),
                () -> assertEquals("ERROR", result.getDetectionStatus())
        );
    }

    // ==================== 检测记录保存 ====================

    @Test
    void shouldSaveRiskRecordWithCorrectFields() throws Exception {
        JsonNode aiResponse = buildAiResponse("FALSE_AD", "MEDIUM", 65, List.of());
        when(aiClientService.checkViolationText(anyString(), anyString()))
                .thenReturn(aiResponse);

        ViolationTextResult result = violationTextService.detectViolation(
                "这家店吃了能治百病", RULE_VERSION);

        // Capture the inserted record via doAnswer
        final ContentRiskRecord[] captured = new ContentRiskRecord[1];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0, ContentRiskRecord.class);
            return 1;
        }).when(riskRecordMapper).insert(
                org.mockito.ArgumentMatchers.<ContentRiskRecord>any());

        violationTextService.saveRecord(
                "这家店吃了能治百病",
                "REVIEW",
                101L,
                1,
                RULE_VERSION,
                result
        );

        verify(riskRecordMapper, times(1)).insert(
                org.mockito.ArgumentMatchers.<ContentRiskRecord>any());
        ContentRiskRecord record = captured[0];
        assertNotNull(record, "Should have captured a record");
        assertAll(
                () -> assertEquals("REVIEW", record.getContentType()),
                () -> assertEquals(101L, record.getContentId()),
                () -> assertEquals(1, record.getContentVersion()),
                () -> assertEquals("FALSE_AD", record.getRiskType()),
                () -> assertEquals("MEDIUM", record.getRiskLevel()),
                () -> assertEquals(65, record.getRiskScore()),
                () -> assertEquals("SUCCESS", record.getDetectionStatus()),
                () -> assertNotNull(record.getMaskedExcerpt())
        );
    }

    @Test
    void shouldStillInsertRecordWhenContentIdIsNull() {
        // ViolationTextService.saveRecord() does not check for null;
        // the null check is at the ReviewService layer (saveViolationRecord).
        ViolationTextResult result = ViolationTextResult.error("test");

        violationTextService.saveRecord(
                "test", "REVIEW", null, 1, RULE_VERSION, result);

        // It still inserts — the null contentId is simply recorded as-is.
        verify(riskRecordMapper, times(1)).insert(
                org.mockito.ArgumentMatchers.<ContentRiskRecord>any());
    }

    // ==================== 查询方法 ====================

    @Test
    void shouldRetrieveLatestRecord() {
        ContentRiskRecord expected = new ContentRiskRecord();
        expected.setId(1L);
        expected.setContentType("REVIEW");
        expected.setContentId(101L);
        when(riskRecordMapper.findLatest("REVIEW", 101L)).thenReturn(expected);

        ContentRiskRecord actual = violationTextService.getLatestRecord("REVIEW", 101L);

        assertSame(expected, actual);
    }

    @Test
    void shouldRetrieveAllRecords() {
        ContentRiskRecord record = new ContentRiskRecord();
        record.setId(1L);
        when(riskRecordMapper.findByContent("REVIEW", 101L))
                .thenReturn(List.of(record));

        List<ContentRiskRecord> records = violationTextService.getRecords("REVIEW", 101L);

        assertEquals(1, records.size());
    }

    // ==================== 测试辅助方法 ====================

    /**
     * 构建模拟的 AI 服务 JSON 响应。
     */
    private JsonNode buildAiResponse(
            String riskType,
            String riskLevel,
            int riskScore,
            List<ViolationTextResult.MatchedRuleInfo> rules
    ) throws Exception {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"riskType\":").append(riskType == null ? "null" : "\"" + riskType + "\"");
        json.append(",\"riskLevel\":\"").append(riskLevel).append("\"");
        json.append(",\"riskScore\":").append(riskScore);
        json.append(",\"detectionStatus\":\"SUCCESS\"");
        json.append(",\"modelName\":\"test-model\"");
        json.append(",\"businessTraceId\":\"trace-test-001\"");

        json.append(",\"matchedRules\":[");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) json.append(",");
            ViolationTextResult.MatchedRuleInfo rule = rules.get(i);
            json.append("{");
            json.append("\"ruleCode\":\"").append(rule.getRuleCode()).append("\"");
            json.append(",\"ruleName\":\"").append(rule.getRuleName()).append("\"");
            json.append(",\"riskType\":\"").append(rule.getRiskType()).append("\"");
            json.append(",\"confidence\":").append(rule.getConfidence());
            json.append(",\"evidenceExcerpt\":\"")
                    .append(rule.getEvidenceExcerpt() != null ? rule.getEvidenceExcerpt() : "")
                    .append("\"");
            json.append("}");
        }
        json.append("]");
        json.append("}");

        return objectMapper.readTree(json.toString());
    }
}
