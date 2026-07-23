package com.foodadvisor.dto.violation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 违规文本检测结果 — 封装检测后的风险信息和命中规则。
 *
 * <p>由 ViolationTextService 返回给 ReviewService，
 * 用于决定评价的状态（直接发布 / 待审核 / 阻止发布）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationTextResult {

    /** 是否有违规风险（riskLevel != LOW） */
    private boolean violation;

    /** 风险等级：LOW / MEDIUM / HIGH */
    private String riskLevel;

    /** 风险分值 0-100 */
    private int riskScore;

    /** 主要风险类型，无风险时为 null */
    private String riskType;

    /** 命中的规则列表 */
    private List<MatchedRuleInfo> matchedRules;

    /** 检测状态：SUCCESS / FALLBACK / ERROR / TIMEOUT */
    private String detectionStatus;

    /** 使用的模型名称 */
    private String modelName;

    /** 业务追踪ID */
    private String businessTraceId;

    /** 错误信息（检测失败时） */
    private String errorMessage;

    /**
     * 单条匹配规则信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedRuleInfo {
        private String ruleCode;
        private String ruleName;
        private String riskType;
        private double confidence;
        private String evidenceExcerpt;
    }

    /**
     * 创建降级检测结果（ai-service 不可用时使用）
     */
    public static ViolationTextResult fallback(boolean violation, String riskLevel, int riskScore) {
        return ViolationTextResult.builder()
                .violation(violation)
                .riskLevel(riskLevel)
                .riskScore(riskScore)
                .riskType(null)
                .matchedRules(List.of())
                .detectionStatus("FALLBACK")
                .modelName("keyword-fallback")
                .businessTraceId(null)
                .errorMessage("AI detection service unavailable, fallback to keyword matching")
                .build();
    }

    /**
     * 创建错误检测结果（检测完全失败时使用，默认放行）
     */
    public static ViolationTextResult error(String errorMessage) {
        return ViolationTextResult.builder()
                .violation(false)
                .riskLevel("LOW")
                .riskScore(0)
                .riskType(null)
                .matchedRules(List.of())
                .detectionStatus("ERROR")
                .modelName(null)
                .businessTraceId(null)
                .errorMessage(errorMessage)
                .build();
    }
}
