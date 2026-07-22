package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.regression.RegressionTestCaseResultResponse;
import com.foodadvisor.dto.regression.RegressionTestRunComparisonResponse;
import com.foodadvisor.dto.regression.RegressionTestRunDetailResponse;
import com.foodadvisor.dto.regression.RegressionTestRunResponse;
import com.foodadvisor.dto.regression.RegressionTestRunSetResponse;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.entity.RegressionTestCaseResult;
import com.foodadvisor.entity.RegressionTestRun;
import com.foodadvisor.entity.RegressionTestRunSet;
import com.foodadvisor.entity.RegressionTestSet;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.RegressionTestCaseMapper;
import com.foodadvisor.mapper.RegressionTestCaseResultMapper;
import com.foodadvisor.mapper.RegressionTestRunMapper;
import com.foodadvisor.mapper.RegressionTestRunSetMapper;
import com.foodadvisor.mapper.RegressionTestSetMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RegressionTestRunQueryService {

    private static final Set<String> RUN_STATUSES =
            Set.of(
                    "PENDING",
                    "RUNNING",
                    "COMPLETED",
                    "PARTIAL",
                    "FAILED"
            );

    private static final Set<String> TERMINAL_RUN_STATUSES =
            Set.of(
                    "COMPLETED",
                    "PARTIAL",
                    "FAILED"
            );

    private static final Set<String> EXECUTION_STATUSES =
            Set.of(
                    "PENDING",
                    "RUNNING",
                    "SUCCESS",
                    "FAILED",
                    "SKIPPED"
            );

    private static final Set<String> ASSERTION_STATUSES =
            Set.of(
                    "PASSED",
                    "FAILED",
                    "NOT_EVALUATED"
            );

    private static final Set<String> COMPARISON_STATUSES =
            Set.of(
                    "NOT_COMPARED",
                    "NEW_FAILURE",
                    "FIXED",
                    "STILL_FAILED",
                    "STILL_PASSED",
                    "NEW_CASE",
                    "REMOVED_CASE"
            );

    private final RegressionTestRunMapper runMapper;

    private final RegressionTestRunSetMapper runSetMapper;

    private final RegressionTestCaseResultMapper caseResultMapper;

    private final RegressionTestSetMapper testSetMapper;

    private final RegressionTestCaseMapper testCaseMapper;

    private final RegressionTestRunPersistenceService persistenceService;

    private final ObjectMapper objectMapper;

    public RegressionTestRunQueryService(
            RegressionTestRunMapper runMapper,
            RegressionTestRunSetMapper runSetMapper,
            RegressionTestCaseResultMapper caseResultMapper,
            RegressionTestSetMapper testSetMapper,
            RegressionTestCaseMapper testCaseMapper,
            RegressionTestRunPersistenceService persistenceService,
            ObjectMapper objectMapper
    ) {
        this.runMapper = runMapper;
        this.runSetMapper = runSetMapper;
        this.caseResultMapper = caseResultMapper;
        this.testSetMapper = testSetMapper;
        this.testCaseMapper = testCaseMapper;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询历史回归运行。
     */
    public Page<RegressionTestRunResponse> queryRuns(
            long pageNum,
            long pageSize,
            String status
    ) {
        validatePage(pageNum, pageSize);

        LambdaQueryWrapper<RegressionTestRun> wrapper =
                new LambdaQueryWrapper<>();

        if (hasText(status)) {
            wrapper.eq(
                    RegressionTestRun::getStatus,
                    normalizeStatus(
                            status,
                            RUN_STATUSES,
                            "INVALID_REGRESSION_RUN_STATUS",
                            "不支持的回归运行状态"
                    )
            );
        }

        wrapper.orderByDesc(
                        RegressionTestRun::getCreatedAt
                )
                .orderByDesc(
                        RegressionTestRun::getId
                );

        Page<RegressionTestRun> entityPage =
                runMapper.selectPage(
                        Page.of(pageNum, pageSize),
                        wrapper
                );

        Page<RegressionTestRunResponse> responsePage =
                new Page<>(
                        entityPage.getCurrent(),
                        entityPage.getSize(),
                        entityPage.getTotal()
                );

        responsePage.setRecords(
                entityPage.getRecords()
                        .stream()
                        .map(this::toRunResponse)
                        .toList()
        );

        return responsePage;
    }

    /**
     * 查询运行详情，包括测试集进度和案例结果。
     */
    public RegressionTestRunDetailResponse getRunDetail(
            Long runId
    ) {
        RegressionTestRun run =
                persistenceService.getRun(runId);

        List<RegressionTestRunSet> runSets =
                runSetMapper.selectList(
                        new LambdaQueryWrapper<RegressionTestRunSet>()
                                .eq(
                                        RegressionTestRunSet::getRunId,
                                        runId
                                )
                                .orderByAsc(
                                        RegressionTestRunSet::getId
                                )
                );

        List<RegressionTestCaseResult> results =
                loadRunResults(runId);

        Map<Long, String> testSetNames =
                loadTestSetNames(
                        runSets.stream()
                                .map(
                                        RegressionTestRunSet::getTestSetId
                                )
                                .toList()
                );

        Map<Long, RegressionTestCase> cases =
                loadCases(
                        results.stream()
                                .map(
                                        RegressionTestCaseResult::getCaseId
                                )
                                .toList()
                );

        return new RegressionTestRunDetailResponse(
                toRunResponse(run),

                runSets.stream()
                        .map(runSet ->
                                toRunSetResponse(
                                        runSet,
                                        testSetNames.get(
                                                runSet.getTestSetId()
                                        )
                                )
                        )
                        .toList(),

                results.stream()
                        .map(result ->
                                toCaseResultResponse(
                                        result,
                                        cases.get(
                                                result.getCaseId()
                                        )
                                )
                        )
                        .toList()
        );
    }

    /**
     * 查询一次运行中的案例结果。
     *
     * 可按执行状态、断言状态和比较状态过滤，
     * 从而直接查询执行错误或新增失败。
     */
    public List<RegressionTestCaseResultResponse> listResults(
            Long runId,
            String executionStatus,
            String assertionStatus,
            String comparisonStatus
    ) {
        persistenceService.getRun(runId);

        LambdaQueryWrapper<RegressionTestCaseResult> wrapper =
                new LambdaQueryWrapper<RegressionTestCaseResult>()
                        .eq(
                                RegressionTestCaseResult::getRunId,
                                runId
                        );

        if (hasText(executionStatus)) {
            wrapper.eq(
                    RegressionTestCaseResult::getExecutionStatus,
                    normalizeStatus(
                            executionStatus,
                            EXECUTION_STATUSES,
                            "INVALID_REGRESSION_EXECUTION_STATUS",
                            "不支持的案例执行状态"
                    )
            );
        }

        if (hasText(assertionStatus)) {
            wrapper.eq(
                    RegressionTestCaseResult::getAssertionStatus,
                    normalizeStatus(
                            assertionStatus,
                            ASSERTION_STATUSES,
                            "INVALID_REGRESSION_ASSERTION_STATUS",
                            "不支持的案例断言状态"
                    )
            );
        }

        if (hasText(comparisonStatus)) {
            wrapper.eq(
                    RegressionTestCaseResult::getComparisonStatus,
                    normalizeStatus(
                            comparisonStatus,
                            COMPARISON_STATUSES,
                            "INVALID_REGRESSION_COMPARISON_STATUS",
                            "不支持的案例比较状态"
                    )
            );
        }

        wrapper.orderByAsc(
                        RegressionTestCaseResult::getRunSetId
                )
                .orderByAsc(
                        RegressionTestCaseResult::getId
                );

        List<RegressionTestCaseResult> results =
                caseResultMapper.selectList(wrapper);

        Map<Long, RegressionTestCase> cases =
                loadCases(
                        results.stream()
                                .map(
                                        RegressionTestCaseResult::getCaseId
                                )
                                .toList()
                );

        return results.stream()
                .map(result ->
                        toCaseResultResponse(
                                result,
                                cases.get(
                                        result.getCaseId()
                                )
                        )
                )
                .toList();
    }

    /**
     * 比较任意两次已经结束的回归运行。
     *
     * 候选运行中缺失、但基线运行中存在的案例，
     * 会在查询阶段生成 REMOVED_CASE。
     */
    public RegressionTestRunComparisonResponse compareRuns(
            Long baselineRunId,
            Long candidateRunId
    ) {
        if (baselineRunId != null
                && baselineRunId.equals(candidateRunId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REGRESSION_RUN_COMPARE_SELF",
                    "不能将同一次回归运行与自身比较"
            );
        }

        RegressionTestRun baselineRun =
                persistenceService.getRun(
                        baselineRunId
                );

        RegressionTestRun candidateRun =
                persistenceService.getRun(
                        candidateRunId
                );

        requireTerminalRun(
                baselineRun,
                "基线运行尚未结束"
        );

        requireTerminalRun(
                candidateRun,
                "候选运行尚未结束"
        );

        List<RegressionTestCaseResult> baselineResults =
                loadRunResults(
                        baselineRunId
                );

        List<RegressionTestCaseResult> candidateResults =
                loadRunResults(
                        candidateRunId
                );

        Map<Long, RegressionTestCaseResult> baselineByCase =
                indexResultsByCase(
                        baselineResults
                );

        Map<Long, RegressionTestCaseResult> candidateByCase =
                indexResultsByCase(
                        candidateResults
                );

        LinkedHashSet<Long> allCaseIds =
                new LinkedHashSet<>();

        allCaseIds.addAll(
                baselineByCase.keySet()
        );

        allCaseIds.addAll(
                candidateByCase.keySet()
        );

        Map<Long, RegressionTestCase> cases =
                loadCases(allCaseIds);

        List<RegressionTestRunComparisonResponse.CaseComparison>
                comparisons =
                new ArrayList<>();

        int newFailureCount = 0;
        int fixedCount = 0;
        int stillFailedCount = 0;
        int stillPassedCount = 0;
        int newCaseCount = 0;
        int removedCaseCount = 0;

        for (Long caseId : allCaseIds) {
            RegressionTestCaseResult baseline =
                    baselineByCase.get(caseId);

            RegressionTestCaseResult candidate =
                    candidateByCase.get(caseId);

            String comparisonStatus =
                    resolveComparisonStatus(
                            baseline,
                            candidate
                    );

            switch (comparisonStatus) {
                case "NEW_FAILURE" ->
                        newFailureCount++;

                case "FIXED" ->
                        fixedCount++;

                case "STILL_FAILED" ->
                        stillFailedCount++;

                case "STILL_PASSED" ->
                        stillPassedCount++;

                case "NEW_CASE" ->
                        newCaseCount++;

                case "REMOVED_CASE" ->
                        removedCaseCount++;

                default -> {
                    // 当前比较方法不会产生其他状态。
                }
            }

            RegressionTestCase testCase =
                    cases.get(caseId);

            String caseCode =
                    firstNonBlank(
                            candidate == null
                                    ? null
                                    : candidate
                                            .getCaseCodeSnapshot(),

                            baseline == null
                                    ? null
                                    : baseline
                                            .getCaseCodeSnapshot(),

                            testCase == null
                                    ? null
                                    : testCase.getCaseCode(),

                            String.valueOf(caseId)
                    );

            String caseName =
                    firstNonBlank(
                            testCase == null
                                    ? null
                                    : testCase.getCaseName(),

                            caseCode
                    );

            String testType =
                    firstNonBlank(
                            candidate == null
                                    ? null
                                    : candidate.getTestType(),

                            baseline == null
                                    ? null
                                    : baseline.getTestType(),

                            null
                    );

            comparisons.add(
                    new RegressionTestRunComparisonResponse
                            .CaseComparison(
                            caseId,
                            caseCode,
                            caseName,
                            testType,

                            baseline == null
                                    ? null
                                    : baseline
                                            .getAssertionStatus(),

                            candidate == null
                                    ? null
                                    : candidate
                                            .getAssertionStatus(),

                            comparisonStatus,

                            baseline == null
                                    ? null
                                    : baseline.getId(),

                            candidate == null
                                    ? null
                                    : candidate.getId()
                    )
            );
        }

        return new RegressionTestRunComparisonResponse(
                baselineRunId,
                candidateRunId,

                new RegressionTestRunComparisonResponse
                        .ComparisonSummary(
                        newFailureCount,
                        fixedCount,
                        stillFailedCount,
                        stillPassedCount,
                        newCaseCount,
                        removedCaseCount
                ),

                comparisons
        );
    }

    private List<RegressionTestCaseResult> loadRunResults(
            Long runId
    ) {
        return caseResultMapper.selectList(
                new LambdaQueryWrapper<RegressionTestCaseResult>()
                        .eq(
                                RegressionTestCaseResult::getRunId,
                                runId
                        )
                        .orderByAsc(
                                RegressionTestCaseResult::getRunSetId
                        )
                        .orderByAsc(
                                RegressionTestCaseResult::getId
                        )
        );
    }

    private Map<Long, RegressionTestCaseResult>
    indexResultsByCase(
            List<RegressionTestCaseResult> results
    ) {
        Map<Long, RegressionTestCaseResult> indexed =
                new LinkedHashMap<>();

        for (RegressionTestCaseResult result : results) {
            if (result.getCaseId() != null) {
                indexed.put(
                        result.getCaseId(),
                        result
                );
            }
        }

        return indexed;
    }

    private Map<Long, RegressionTestCase> loadCases(
            Collection<Long> caseIds
    ) {
        LinkedHashSet<Long> validIds =
                new LinkedHashSet<>();

        if (caseIds != null) {
            for (Long caseId : caseIds) {
                if (caseId != null) {
                    validIds.add(caseId);
                }
            }
        }

        if (validIds.isEmpty()) {
            return Map.of();
        }

        List<RegressionTestCase> cases =
                testCaseMapper.selectBatchIds(
                        validIds
                );

        Map<Long, RegressionTestCase> indexed =
                new LinkedHashMap<>();

        for (RegressionTestCase testCase : cases) {
            indexed.put(
                    testCase.getId(),
                    testCase
            );
        }

        return indexed;
    }

    private Map<Long, String> loadTestSetNames(
            Collection<Long> testSetIds
    ) {
        LinkedHashSet<Long> validIds =
                new LinkedHashSet<>();

        if (testSetIds != null) {
            for (Long testSetId : testSetIds) {
                if (testSetId != null) {
                    validIds.add(testSetId);
                }
            }
        }

        if (validIds.isEmpty()) {
            return Map.of();
        }

        List<RegressionTestSet> testSets =
                testSetMapper.selectBatchIds(
                        validIds
                );

        Map<Long, String> names =
                new LinkedHashMap<>();

        for (RegressionTestSet testSet : testSets) {
            names.put(
                    testSet.getId(),
                    testSet.getName()
            );
        }

        return names;
    }

    private String resolveComparisonStatus(
            RegressionTestCaseResult baseline,
            RegressionTestCaseResult candidate
    ) {
        if (baseline == null) {
            return "NEW_CASE";
        }

        if (candidate == null) {
            return "REMOVED_CASE";
        }

        boolean baselineFailed =
                isFailedResult(baseline);

        boolean candidateFailed =
                isFailedResult(candidate);

        if (baselineFailed && candidateFailed) {
            return "STILL_FAILED";
        }

        if (baselineFailed) {
            return "FIXED";
        }

        if (candidateFailed) {
            return "NEW_FAILURE";
        }

        return "STILL_PASSED";
    }

    private boolean isFailedResult(
            RegressionTestCaseResult result
    ) {
        return result == null
                || !"SUCCESS".equalsIgnoreCase(
                        result.getExecutionStatus()
                )
                || !"PASSED".equalsIgnoreCase(
                        result.getAssertionStatus()
                );
    }

    private void requireTerminalRun(
            RegressionTestRun run,
            String message
    ) {
        if (run == null
                || !TERMINAL_RUN_STATUSES.contains(
                        normalizeNullable(
                                run.getStatus()
                        )
                )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REGRESSION_RUN_NOT_COMPLETED",
                    message
            );
        }
    }

    private RegressionTestRunResponse toRunResponse(
            RegressionTestRun run
    ) {
        return new RegressionTestRunResponse(
                run.getId(),
                run.getRunName(),
                run.getBaselineRunId(),
                run.getStatus(),

                readJsonOrDefault(
                        run.getModelVersions(),
                        "{}"
                ),

                readJsonOrDefault(
                        run.getPromptVersions(),
                        "{}"
                ),

                readJsonOrDefault(
                        run.getAlgorithmVersions(),
                        "{}"
                ),

                readJsonOrDefault(
                        run.getDataVersions(),
                        "{}"
                ),

                readJsonOrDefault(
                        run.getRequestSnapshot(),
                        "{}"
                ),

                run.getRequestedSetCount(),
                run.getCompletedSetCount(),
                run.getRequestedCaseCount(),
                run.getCompletedCaseCount(),

                run.getPassedCount(),
                run.getAssertionFailedCount(),
                run.getExecutionErrorCount(),

                run.getProgressPercent(),
                run.getErrorMessage(),

                run.getCreatedBy(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }

    private RegressionTestRunSetResponse toRunSetResponse(
            RegressionTestRunSet runSet,
            String testSetName
    ) {
        return new RegressionTestRunSetResponse(
                runSet.getId(),
                runSet.getRunId(),
                runSet.getTestSetId(),
                testSetName,
                runSet.getTestType(),
                runSet.getStatus(),

                runSet.getModelName(),
                runSet.getModelVersion(),
                runSet.getPromptVersion(),
                runSet.getAlgorithmVersion(),
                runSet.getDataVersion(),

                runSet.getRequestedCaseCount(),
                runSet.getCompletedCaseCount(),
                runSet.getPassedCount(),
                runSet.getAssertionFailedCount(),
                runSet.getExecutionErrorCount(),

                runSet.getProgressPercent(),
                runSet.getErrorMessage(),

                runSet.getStartedAt(),
                runSet.getCompletedAt(),
                runSet.getCreatedAt(),
                runSet.getUpdatedAt()
        );
    }

    private RegressionTestCaseResultResponse
    toCaseResultResponse(
            RegressionTestCaseResult result,
            RegressionTestCase testCase
    ) {
        String caseCode =
                firstNonBlank(
                        result.getCaseCodeSnapshot(),

                        testCase == null
                                ? null
                                : testCase.getCaseCode(),

                        String.valueOf(
                                result.getCaseId()
                        )
                );

        String caseName =
                firstNonBlank(
                        testCase == null
                                ? null
                                : testCase.getCaseName(),

                        caseCode
                );

        return new RegressionTestCaseResultResponse(
                result.getId(),
                result.getRunId(),
                result.getRunSetId(),
                result.getCaseId(),
                result.getBaselineResultId(),

                caseCode,
                caseName,
                result.getTestType(),

                result.getExecutionStatus(),
                result.getAssertionStatus(),
                result.getComparisonStatus(),

                result.getModelName(),
                result.getModelVersion(),
                result.getPromptVersion(),
                result.getAlgorithmVersion(),
                result.getDataVersion(),

                readJsonOrDefault(
                        result.getInputSnapshot(),
                        "{}"
                ),

                readJsonOrDefault(
                        result.getExpectedSnapshot(),
                        "{}"
                ),

                readJsonOrDefault(
                        result.getActualOutput(),
                        "{}"
                ),

                readJsonOrDefault(
                        result.getMetrics(),
                        "{}"
                ),

                readJsonOrDefault(
                        result.getFailureReasons(),
                        "[]"
                ),

                result.getTraceId(),
                result.getDurationMs(),
                result.getErrorMessage(),

                result.getStartedAt(),
                result.getCompletedAt(),
                result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }

    private JsonNode readJsonOrDefault(
            String value,
            String defaultValue
    ) {
        try {
            return objectMapper.readTree(
                    value == null || value.isBlank()
                            ? defaultValue
                            : value
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "回归测试结果JSON解析失败",
                    exception
            );
        }
    }

    private String normalizeStatus(
            String value,
            Set<String> allowedStatuses,
            String errorCode,
            String errorMessage
    ) {
        String normalized =
                normalizeNullable(value);

        if (normalized == null
                || !allowedStatuses.contains(
                        normalized
                )) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    errorCode,
                    errorMessage
            );
        }

        return normalized;
    }

    private String normalizeNullable(
            String value
    ) {
        return value == null
                || value.isBlank()
                ? null
                : value.trim()
                        .toUpperCase(Locale.ROOT);
    }

    private void validatePage(
            long pageNum,
            long pageSize
    ) {
        if (pageNum < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_NUM",
                    "pageNum必须大于等于1"
            );
        }

        if (pageSize < 1 || pageSize > 200) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE_SIZE",
                    "pageSize必须在1到200之间"
            );
        }
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null
                    && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }
}