package com.foodadvisor.dto.competitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 周边竞品对比响应 — 返回给前端的完整对比结果。
 *
 * 包含两部分数据：
 * 1. merchantData: 从数据库查询的各商家统计指标（供前端渲染图表）
 * 2. aiAnalysis: 从 AI 服务获取的自然语言分析（供前端展示文字总结）
 *
 * 验收准则对齐：
 * - AC-2: 对比结果包含价格、评分、好评率、评价数量等维度
 * - AC-3: AI 分析突出至少一项优势或短板，无明显差异时明确说明
 * - AC-6: 前端可据此渲染图表和文字
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorComparisonResponse {

    /** 发起对比的商家 ID（本店） */
    private Long merchantId;

    /** 对比状态：SUCCESS / FAILED */
    private String comparisonStatus;

    // ---- 数据库查询的统计数据（供前端渲染图表） ----
    /** 本店和竞品的核心指标数据，第一个元素为本店 */
    private List<CompetitorMerchantVO> merchantData;

    // ---- AI 生成的自然语言分析（供前端展示文字） ----
    /** AI 为每家商家生成的优势/短板分析 */
    private List<AiMerchantAnalysis> aiMerchantAnalyses;

    /** AI 生成的横向对比总结（2~3句） */
    private String aiSummaryText;

    /** AI 生成的改进建议（针对本店，最多3条） */
    private List<String> aiImprovementSuggestions;

    /** AI 模型名称 */
    private String modelName;

    /** AI 调用追踪 ID */
    private String businessTraceId;

    /** 错误信息（仅在 FAILED 时有值） */
    private String errorMessage;

    // ---- 内嵌类 ----

    /**
     * AI 对单家商家的分析结果。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiMerchantAnalysis {
        /** 商家 ID */
        private Long merchantId;
        /** 商家名称 */
        private String merchantName;
        /** 相对于竞品的优势列表 */
        private List<String> strengths;
        /** 相对于竞品的短板列表 */
        private List<String> weaknesses;
        /** 综合评价（1~2句） */
        private String overallAssessment;
    }
}
