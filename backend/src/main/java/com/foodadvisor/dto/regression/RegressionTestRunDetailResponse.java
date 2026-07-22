package com.foodadvisor.dto.regression;

import java.util.List;

public record RegressionTestRunDetailResponse(
        RegressionTestRunResponse run,
        List<RegressionTestRunSetResponse> testSets,
        List<RegressionTestCaseResultResponse> results
) {
}