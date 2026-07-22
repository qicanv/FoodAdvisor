package com.foodadvisor.dto.regression;

import java.util.List;

public record RegressionTestRunComparisonResponse(
        Long baselineRunId,
        Long candidateRunId,
        ComparisonSummary summary,
        List<CaseComparison> cases
) {

    public record ComparisonSummary(
            Integer newFailureCount,
            Integer fixedCount,
            Integer stillFailedCount,
            Integer stillPassedCount,
            Integer newCaseCount,
            Integer removedCaseCount
    ) {
    }

    public record CaseComparison(
            Long caseId,
            String caseCode,
            String caseName,
            String testType,
            String baselineAssertionStatus,
            String candidateAssertionStatus,
            String comparisonStatus,
            Long baselineResultId,
            Long candidateResultId
    ) {
    }
}