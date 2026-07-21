package com.foodadvisor.dto.evaluation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 两次推荐评测运行的对比结果。
 */
public record RecommendationEvalRunComparisonResponse(

        Long baselineRunId,

        Long candidateRunId,

        Long datasetId,

        RunSnapshot baseline,

        RunSnapshot candidate,

        MetricComparison metrics,

        List<CaseComparison> cases,

        List<Long> improvedCaseIds,

        List<Long> regressedCaseIds,

        List<Long> unchangedCaseIds,

        List<Long> addedCaseIds,

        List<Long> removedCaseIds
) {

    /**
     * 运行所使用的模型、算法和数据版本。
     */
    public record RunSnapshot(
            String status,
            String modelName,
            String modelVersion,
            String promptVersion,
            String algorithmVersion,
            String dataVersion
    ) {
    }

    /**
     * 运行级指标变化。
     *
     * change = candidate - baseline
     */
    public record MetricComparison(
            BigDecimal baselineConstraintAccuracy,
            BigDecimal candidateConstraintAccuracy,
            BigDecimal constraintAccuracyChange,

            Integer baselineExactMatchCaseCount,
            Integer candidateExactMatchCaseCount,
            Integer exactMatchCaseCountChange,

            Integer baselineFailedCaseCount,
            Integer candidateFailedCaseCount,
            Integer failedCaseCountChange,

            Integer baselineNoResultCaseCount,
            Integer candidateNoResultCaseCount,
            Integer noResultCaseCountChange,

            Integer baselineReturnedRecommendationCount,
            Integer candidateReturnedRecommendationCount,
            Integer returnedRecommendationCountChange,

            Integer baselineUniqueMerchantCount,
            Integer candidateUniqueMerchantCount,
            Integer uniqueMerchantCountChange
    ) {
    }

    /**
     * 单个测试案例在两次运行中的变化。
     */
    public record CaseComparison(
            Long caseId,

            Long baselineResultId,
            Long candidateResultId,

            String baselineStatus,
            String candidateStatus,

            BigDecimal baselineConstraintAccuracy,
            BigDecimal candidateConstraintAccuracy,
            BigDecimal constraintAccuracyChange,

            Integer baselineResultCount,
            Integer candidateResultCount,
            Integer resultCountChange,

            String changeType
    ) {
    }
}