package com.foodadvisor.dto.feedback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 商家分析结果反馈统计视图对象（EPIC-06 Story 5 - AC 4）
 *
 * 管理员可按 AI 功能和反馈类型汇总查看。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisFeedbackStatisticsVO {

    /** 总反馈数 */
    private Long totalCount;

    /** 准确反馈数 */
    private Long accurateCount;

    /** 不准确反馈数 */
    private Long inaccurateCount;

    /** 准确率（0~1） */
    private Double accuracyRate;

    /** 按分析类型分组的统计 */
    private List<AnalysisTypeStat> byAnalysisType;

    /**
     * 单个分析类型的统计项。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisTypeStat {

        /** 分析类型 */
        private String analysisType;

        /** 分析类型中文名 */
        private String analysisTypeText;

        /** 该类型总反馈数 */
        private Long totalCount;

        /** 该类型准确反馈数 */
        private Long accurateCount;

        /** 该类型不准确反馈数 */
        private Long inaccurateCount;

        /** 该类型准确率 */
        private Double accuracyRate;
    }
}
