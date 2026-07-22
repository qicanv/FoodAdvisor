package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodadvisor.dto.regression.RegressionExecutionResult;
import com.foodadvisor.dto.regression.RegressionTestRunRequest;
import com.foodadvisor.dto.regression.RegressionTestRunResponse;
import com.foodadvisor.dto.regression.RegressionVersionSnapshot;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.entity.RegressionTestCaseResult;
import com.foodadvisor.entity.RegressionTestRun;
import com.foodadvisor.entity.RegressionTestRunSet;
import com.foodadvisor.entity.RegressionTestSet;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.RegressionCaseExecutionException;
import com.foodadvisor.mapper.RegressionTestCaseMapper;
import com.foodadvisor.mapper.RegressionTestCaseResultMapper;
import com.foodadvisor.mapper.RegressionTestSetMapper;
import com.foodadvisor.trace.AiTraceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通用回归测试批量运行编排服务。
 *
 * 注意：
 * 本服务不能添加 @Transactional。
 * 任务、测试集进度和案例结果均通过
 * RegressionTestRunPersistenceService 的 REQUIRES_NEW 方法保存，
 * 从而保证中途失败时已完成结果不会整体回滚。
 */
@Service
public class RegressionTestRunService {

    private static final Set<String> TERMINAL_BASELINE_STATUSES =
            Set.of(
                    "COMPLETED",
                    "PARTIAL",
                    "FAILED"
            );

    private static final Set<String> ASSERTION_STATUSES =
            Set.of(
                    "PASSED",
                    "FAILED",
                    "NOT_EVALUATED"
            );

    private final RegressionTestSetMapper testSetMapper;

    private final RegressionTestCaseMapper testCaseMapper;

    private final RegressionTestCaseResultMapper caseResultMapper;

    private final RegressionVersionSnapshotService versionSnapshotService;

    private final RegressionTestRunPersistenceService persistenceService;

    private final ObjectMapper objectMapper;

    private final Map<String, RegressionTestCaseExecutor> executorRegistry;

    public RegressionTestRunService(
            RegressionTestSetMapper testSetMapper,
            RegressionTestCaseMapper testCaseMapper,
            RegressionTestCaseResultMapper caseResultMapper,
            RegressionVersionSnapshotService versionSnapshotService,
            RegressionTestRunPersistenceService persistenceService,
            ObjectMapper objectMapper,
            List<RegressionTestCaseExecutor> executors
    ) {
        this.testSetMapper = testSetMapper;
        this.testCaseMapper = testCaseMapper;
        this.caseResultMapper = caseResultMapper;
        this.versionSnapshotService = versionSnapshotService;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
        this.executorRegistry =
                buildExecutorRegistry(executors);
    }

    /**
     * 同步执行一次批量回归测试。
     */
    public RegressionTestRunResponse executeRun(
            RegressionTestRunRequest request,
            Long createdBy
    ) {
        validateRequest(request);

        List<Long> requestedSetIds =
                distinctTestSetIds(
                        request.testSetIds()
                );

        List<RegressionTestSet> testSets =
                loadTestSets(requestedSetIds);

        Map<Long, List<RegressionTestCase>> casesBySet =
                loadEnabledCases(testSets);

        validateExecutors(testSets);

        Map<String, RegressionVersionSnapshot> snapshots =
                versionSnapshotService.resolveAll(
                        testSets.stream()
                                .map(RegressionTestSet::getTestType)
                                .toList()
                );

        Map<Long, RegressionTestCaseResult> baselineResults =
                loadBaselineResults(
                        request.baselineRunId()
                );

        OffsetDateTime now =
                OffsetDateTime.now();

        int requestedCaseCount =
                casesBySet.values()
                        .stream()
                        .mapToInt(List::size)
                        .sum();

        RegressionTestRun run =
                buildInitialRun(
                        request,
                        createdBy,
                        testSets,
                        snapshots,
                        requestedCaseCount,
                        now
                );

        List<RegressionTestRunSet> runSets =
                buildInitialRunSets(
                        testSets,
                        casesBySet,
                        snapshots,
                        now
                );

        persistenceService.createRunWithSets(
                run,
                runSets
        );

        RunCounters runCounters =
                new RunCounters();

        List<RegressionTestCaseResult> completedResults =
                new ArrayList<>();

        for (int index = 0;
             index < testSets.size();
             index++) {

            RegressionTestSet testSet =
                    testSets.get(index);

            RegressionTestRunSet runSet =
                    runSets.get(index);

            List<RegressionTestCase> cases =
                    casesBySet.getOrDefault(
                            testSet.getId(),
                            List.of()
                    );

            RegressionVersionSnapshot snapshot =
                    snapshots.get(
                            normalizeTestType(
                                    testSet.getTestType()
                            )
                    );

            RegressionTestCaseExecutor executor =
                    executorRegistry.get(
                            normalizeTestType(
                                    testSet.getTestType()
                            )
                    );

            executeRunSet(
                    run,
                    runSet,
                    testSet,
                    cases,
                    snapshot,
                    executor,
                    request.executionOptions(),
                    request.baselineRunId() != null,
                    baselineResults,
                    createdBy,
                    runCounters,
                    completedResults
            );

            runCounters.completedSetCount++;

            updateRunningRun(
                    run,
                    requestedCaseCount,
                    testSets.size(),
                    runCounters
            );
        }

        completeRun(
                run,
                requestedCaseCount,
                testSets.size(),
                runCounters,
                completedResults
        );

        return toRunResponse(run);
    }

    private void executeRunSet(
            RegressionTestRun run,
            RegressionTestRunSet runSet,
            RegressionTestSet testSet,
            List<RegressionTestCase> cases,
            RegressionVersionSnapshot snapshot,
            RegressionTestCaseExecutor executor,
            JsonNode executionOptions,
            boolean comparisonEnabled,
            Map<Long, RegressionTestCaseResult> baselineResults,
            Long createdBy,
            RunCounters runCounters,
            List<RegressionTestCaseResult> completedResults
    ) {
        OffsetDateTime startedAt =
                OffsetDateTime.now();

        runSet.setStatus("RUNNING");
        runSet.setStartedAt(startedAt);
        runSet.setUpdatedAt(startedAt);

        persistenceService.updateRunSet(runSet);

        SetCounters setCounters =
                new SetCounters();

        for (RegressionTestCase testCase : cases) {
            RegressionTestCaseResult baseline =
                    baselineResults.get(
                            testCase.getId()
                    );

            AiTraceContext traceContext =
                    AiTraceContext.create(
                            "regression-run-"
                                    + run.getId()
                                    + "-case-"
                                    + testCase.getId(),
                            null,
                            createdBy,
                            "REGRESSION_"
                                    + normalizeTestType(
                                            testSet.getTestType()
                                    )
                    );

            RegressionTestCaseResult caseResult =
                    createRunningCaseResult(
                            run,
                            runSet,
                            testSet,
                            testCase,
                            snapshot,
                            baseline,
                            traceContext
                    );

            persistenceService.createCaseResult(
                    caseResult
            );

            long startedNanos =
                    System.nanoTime();

            try {
                RegressionExecutionResult executionResult =
                        executor.execute(
                                testCase,
                                snapshot,
                                executionOptions,
                                traceContext
                        );

                applySuccessfulExecution(
                        caseResult,
                        executionResult,
                        snapshot,
                        traceContext
                );
            } catch (
                    RegressionCaseExecutionException exception
            ) {
                applyStructuredExecutionFailure(
                        caseResult,
                        exception,
                        snapshot,
                        traceContext
                );
            } catch (Exception exception) {
                applyExecutionFailure(
                        caseResult,
                        exception,
                        snapshot,
                        traceContext
                );
            }

            caseResult.setDurationMs(
                    elapsedMilliseconds(
                            startedNanos
                    )
            );

            caseResult.setComparisonStatus(
                    resolveComparisonStatus(
                            comparisonEnabled,
                            baseline,
                            caseResult
                    )
            );

            OffsetDateTime completedAt =
                    OffsetDateTime.now();

            caseResult.setCompletedAt(completedAt);
            caseResult.setUpdatedAt(completedAt);

            persistenceService.updateCaseResult(
                    caseResult
            );

            completedResults.add(caseResult);

            updateCounters(
                    caseResult,
                    setCounters,
                    runCounters
            );

            updateRunningRunSet(
                    runSet,
                    setCounters
            );

            updateRunningRun(
                    run,
                    run.getRequestedCaseCount(),
                    run.getRequestedSetCount(),
                    runCounters
            );
        }

        completeRunSet(
                runSet,
                setCounters
        );
    }

    private RegressionTestCaseResult createRunningCaseResult(
            RegressionTestRun run,
            RegressionTestRunSet runSet,
            RegressionTestSet testSet,
            RegressionTestCase testCase,
            RegressionVersionSnapshot snapshot,
            RegressionTestCaseResult baseline,
            AiTraceContext traceContext
    ) {
        OffsetDateTime now =
                OffsetDateTime.now();

        RegressionTestCaseResult result =
                new RegressionTestCaseResult();

        result.setRunId(run.getId());
        result.setRunSetId(runSet.getId());
        result.setCaseId(testCase.getId());

        result.setBaselineResultId(
                baseline == null
                        ? null
                        : baseline.getId()
        );

        result.setCaseCodeSnapshot(
                testCase.getCaseCode()
        );

        result.setTestType(
                normalizeTestType(
                        testSet.getTestType()
                )
        );

        result.setExecutionStatus("RUNNING");
        result.setAssertionStatus("NOT_EVALUATED");
        result.setComparisonStatus("NOT_COMPARED");

        result.setModelName(
                snapshot.modelName()
        );
        result.setModelVersion(
                snapshot.modelVersion()
        );
        result.setPromptVersion(
                snapshot.promptVersion()
        );
        result.setAlgorithmVersion(
                snapshot.algorithmVersion()
        );
        result.setDataVersion(
                testSet.getDataVersion()
        );

        result.setInputSnapshot(
                defaultJsonObject(
                        testCase.getInputPayload()
                )
        );

        result.setExpectedSnapshot(
                defaultJsonObject(
                        testCase.getExpectedOutput()
                )
        );

        result.setActualOutput("{}");
        result.setMetrics("{}");
        result.setFailureReasons("[]");

        result.setTraceId(
                traceContext.traceId()
        );

        result.setDurationMs(null);
        result.setErrorMessage(null);

        result.setStartedAt(now);
        result.setCompletedAt(null);
        result.setCreatedAt(now);
        result.setUpdatedAt(now);

        return result;
    }

    private void applySuccessfulExecution(
            RegressionTestCaseResult result,
            RegressionExecutionResult execution,
            RegressionVersionSnapshot snapshot,
            AiTraceContext traceContext
    ) {
        if (execution == null) {
            throw new IllegalStateException(
                    "回归测试执行器返回结果不能为空"
            );
        }

        String assertionStatus =
                normalizeAssertionStatus(
                        execution.assertionStatus()
                );

        /*
         * NOT_EVALUATED 不是正常完成的断言结果。
         * 将其作为执行失败处理，避免错误计入成功案例。
         */
        if ("NOT_EVALUATED".equals(assertionStatus)) {
            throw new IllegalStateException(
                    "回归测试断言未完成"
            );
        }

        result.setExecutionStatus("SUCCESS");
        result.setAssertionStatus(
                assertionStatus
        );

        result.setActualOutput(
                jsonOrDefault(
                        execution.actualOutput(),
                        "{}"
                )
        );

        result.setMetrics(
                jsonOrDefault(
                        execution.metrics(),
                        "{}"
                )
        );

        result.setFailureReasons(
                jsonArrayOrDefault(
                        execution.failureReasons()
                )
        );

        result.setModelName(
                textOrDefault(
                        execution.modelName(),
                        snapshot.modelName()
                )
        );

        result.setModelVersion(
                textOrDefault(
                        execution.modelVersion(),
                        snapshot.modelVersion()
                )
        );

        /*
         * 执行器明确返回 null 时，可能表示 local 模式没有使用提示词。
         * 因此不强制回填快照值。
         */
        result.setPromptVersion(
                execution.promptVersion()
        );

        result.setTraceId(
                textOrDefault(
                        execution.traceId(),
                        traceContext.traceId()
                )
        );

        result.setErrorMessage(null);
    }

    private void applyStructuredExecutionFailure(
            RegressionTestCaseResult result,
            RegressionCaseExecutionException exception,
            RegressionVersionSnapshot snapshot,
            AiTraceContext traceContext
    ) {
        result.setExecutionStatus("FAILED");
        result.setAssertionStatus("NOT_EVALUATED");

        result.setActualOutput(
                jsonOrDefault(
                        exception.getActualOutput(),
                        "{}"
                )
        );

        result.setMetrics("{}");

        result.setFailureReasons(
                executionFailureReasons(
                        exception
                )
        );

        result.setModelName(
                textOrDefault(
                        exception.getModelName(),
                        snapshot.modelName()
                )
        );

        result.setModelVersion(
                textOrDefault(
                        exception.getModelVersion(),
                        snapshot.modelVersion()
                )
        );

        result.setPromptVersion(
                exception.getPromptVersion()
        );

        result.setTraceId(
                textOrDefault(
                        exception.getTraceId(),
                        traceContext.traceId()
                )
        );

        result.setErrorMessage(
                errorMessage(exception)
        );
    }

    private void applyExecutionFailure(
            RegressionTestCaseResult result,
            Exception exception,
            RegressionVersionSnapshot snapshot,
            AiTraceContext traceContext
    ) {
        result.setExecutionStatus("FAILED");
        result.setAssertionStatus("NOT_EVALUATED");

        result.setActualOutput("{}");
        result.setMetrics("{}");

        result.setFailureReasons(
                executionFailureReasons(
                        exception
                )
        );

        result.setModelName(
                snapshot.modelName()
        );

        result.setModelVersion(
                snapshot.modelVersion()
        );

        result.setPromptVersion(
                snapshot.promptVersion()
        );

        result.setTraceId(
                traceContext.traceId()
        );

        result.setErrorMessage(
                errorMessage(exception)
        );
    }

    private void updateCounters(
            RegressionTestCaseResult result,
            SetCounters setCounters,
            RunCounters runCounters
    ) {
        setCounters.completedCaseCount++;
        runCounters.completedCaseCount++;

        if ("FAILED".equals(
                result.getExecutionStatus()
        )) {
            setCounters.executionErrorCount++;
            runCounters.executionErrorCount++;
            return;
        }

        if ("PASSED".equals(
                result.getAssertionStatus()
        )) {
            setCounters.passedCount++;
            runCounters.passedCount++;
            return;
        }

        setCounters.assertionFailedCount++;
        runCounters.assertionFailedCount++;
    }

    private void updateRunningRunSet(
            RegressionTestRunSet runSet,
            SetCounters counters
    ) {
        runSet.setCompletedCaseCount(
                counters.completedCaseCount
        );

        runSet.setPassedCount(
                counters.passedCount
        );

        runSet.setAssertionFailedCount(
                counters.assertionFailedCount
        );

        runSet.setExecutionErrorCount(
                counters.executionErrorCount
        );

        runSet.setProgressPercent(
                progressPercent(
                        counters.completedCaseCount,
                        runSet.getRequestedCaseCount()
                )
        );

        runSet.setUpdatedAt(
                OffsetDateTime.now()
        );

        persistenceService.updateRunSet(runSet);
    }

    private void completeRunSet(
            RegressionTestRunSet runSet,
            SetCounters counters
    ) {
        runSet.setCompletedCaseCount(
                counters.completedCaseCount
        );

        runSet.setPassedCount(
                counters.passedCount
        );

        runSet.setAssertionFailedCount(
                counters.assertionFailedCount
        );

        runSet.setExecutionErrorCount(
                counters.executionErrorCount
        );

        runSet.setProgressPercent(
                BigDecimal.valueOf(100)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
        );

        runSet.setStatus(
                terminalStatus(
                        counters.completedCaseCount,
                        counters.executionErrorCount
                )
        );

        runSet.setErrorMessage(
                counters.executionErrorCount == 0
                        ? null
                        : counters.executionErrorCount
                                + "个案例执行失败"
        );

        OffsetDateTime now =
                OffsetDateTime.now();

        runSet.setCompletedAt(now);
        runSet.setUpdatedAt(now);

        persistenceService.updateRunSet(runSet);
    }

    private void updateRunningRun(
            RegressionTestRun run,
            int requestedCaseCount,
            int requestedSetCount,
            RunCounters counters
    ) {
        run.setCompletedSetCount(
                counters.completedSetCount
        );

        run.setCompletedCaseCount(
                counters.completedCaseCount
        );

        run.setPassedCount(
                counters.passedCount
        );

        run.setAssertionFailedCount(
                counters.assertionFailedCount
        );

        run.setExecutionErrorCount(
                counters.executionErrorCount
        );

        run.setProgressPercent(
                overallProgressPercent(
                        counters.completedCaseCount,
                        requestedCaseCount,
                        counters.completedSetCount,
                        requestedSetCount
                )
        );

        run.setUpdatedAt(
                OffsetDateTime.now()
        );

        persistenceService.updateRun(run);
    }

    private void completeRun(
            RegressionTestRun run,
            int requestedCaseCount,
            int requestedSetCount,
            RunCounters counters,
            List<RegressionTestCaseResult> completedResults
    ) {
        run.setCompletedSetCount(
                counters.completedSetCount
        );

        run.setCompletedCaseCount(
                counters.completedCaseCount
        );

        run.setPassedCount(
                counters.passedCount
        );

        run.setAssertionFailedCount(
                counters.assertionFailedCount
        );

        run.setExecutionErrorCount(
                counters.executionErrorCount
        );

        run.setProgressPercent(
                BigDecimal.valueOf(100)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
        );

        run.setStatus(
                terminalStatus(
                        counters.completedCaseCount,
                        counters.executionErrorCount
                )
        );

        run.setErrorMessage(
                counters.executionErrorCount == 0
                        ? null
                        : counters.executionErrorCount
                                + "个案例执行失败"
        );

        appendObservedVersions(
                run,
                completedResults
        );

        OffsetDateTime now =
                OffsetDateTime.now();

        run.setCompletedAt(now);
        run.setUpdatedAt(now);

        persistenceService.updateRun(run);
    }

    private String terminalStatus(
            int completedCount,
            int executionErrorCount
    ) {
        if (executionErrorCount == 0) {
            return "COMPLETED";
        }

        if (completedCount > executionErrorCount) {
            return "PARTIAL";
        }

        return "FAILED";
    }

    private String resolveComparisonStatus(
            boolean comparisonEnabled,
            RegressionTestCaseResult baseline,
            RegressionTestCaseResult candidate
    ) {
        if (!comparisonEnabled) {
            return "NOT_COMPARED";
        }

        if (baseline == null) {
            return "NEW_CASE";
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
                || !"SUCCESS".equals(
                        result.getExecutionStatus()
                )
                || !"PASSED".equals(
                        result.getAssertionStatus()
                );
    }

    private RegressionTestRun buildInitialRun(
            RegressionTestRunRequest request,
            Long createdBy,
            List<RegressionTestSet> testSets,
            Map<String, RegressionVersionSnapshot> snapshots,
            int requestedCaseCount,
            OffsetDateTime now
    ) {
        RegressionTestRun run =
                new RegressionTestRun();

        run.setRunName(
                cleanRunName(
                        request.runName(),
                        now
                )
        );

        run.setBaselineRunId(
                request.baselineRunId()
        );

        run.setStatus("RUNNING");

        run.setModelVersions(
                buildModelVersionsJson(
                        snapshots
                )
        );

        run.setPromptVersions(
                buildPromptVersionsJson(
                        snapshots
                )
        );

        run.setAlgorithmVersions(
                buildAlgorithmVersionsJson(
                        snapshots
                )
        );

        run.setDataVersions(
                buildDataVersionsJson(
                        testSets
                )
        );

        run.setRequestSnapshot(
                writeJson(request)
        );

        run.setRequestedSetCount(
                testSets.size()
        );

        run.setCompletedSetCount(0);

        run.setRequestedCaseCount(
                requestedCaseCount
        );

        run.setCompletedCaseCount(0);
        run.setPassedCount(0);
        run.setAssertionFailedCount(0);
        run.setExecutionErrorCount(0);

        run.setProgressPercent(
                BigDecimal.ZERO.setScale(2)
        );

        run.setErrorMessage(null);
        run.setCreatedBy(createdBy);
        run.setStartedAt(now);
        run.setCompletedAt(null);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);

        return run;
    }

    private List<RegressionTestRunSet> buildInitialRunSets(
            List<RegressionTestSet> testSets,
            Map<Long, List<RegressionTestCase>> casesBySet,
            Map<String, RegressionVersionSnapshot> snapshots,
            OffsetDateTime now
    ) {
        List<RegressionTestRunSet> runSets =
                new ArrayList<>();

        for (RegressionTestSet testSet : testSets) {
            RegressionVersionSnapshot snapshot =
                    snapshots.get(
                            normalizeTestType(
                                    testSet.getTestType()
                            )
                    );

            RegressionTestRunSet runSet =
                    new RegressionTestRunSet();

            runSet.setTestSetId(
                    testSet.getId()
            );

            runSet.setTestType(
                    normalizeTestType(
                            testSet.getTestType()
                    )
            );

            runSet.setStatus("PENDING");

            runSet.setModelName(
                    snapshot.modelName()
            );

            runSet.setModelVersion(
                    snapshot.modelVersion()
            );

            runSet.setPromptVersion(
                    snapshot.promptVersion()
            );

            runSet.setAlgorithmVersion(
                    snapshot.algorithmVersion()
            );

            runSet.setDataVersion(
                    testSet.getDataVersion()
            );

            runSet.setRequestedCaseCount(
                    casesBySet.getOrDefault(
                            testSet.getId(),
                            List.of()
                    ).size()
            );

            runSet.setCompletedCaseCount(0);
            runSet.setPassedCount(0);
            runSet.setAssertionFailedCount(0);
            runSet.setExecutionErrorCount(0);

            runSet.setProgressPercent(
                    BigDecimal.ZERO.setScale(2)
            );

            runSet.setErrorMessage(null);
            runSet.setStartedAt(null);
            runSet.setCompletedAt(null);
            runSet.setCreatedAt(now);
            runSet.setUpdatedAt(now);

            runSets.add(runSet);
        }

        return runSets;
    }

    private List<RegressionTestSet> loadTestSets(
            List<Long> requestedIds
    ) {
        List<RegressionTestSet> found =
                testSetMapper.selectBatchIds(
                        requestedIds
                );

        Map<Long, RegressionTestSet> byId =
                new LinkedHashMap<>();

        for (RegressionTestSet testSet : found) {
            byId.put(
                    testSet.getId(),
                    testSet
            );
        }

        List<RegressionTestSet> ordered =
                new ArrayList<>();

        for (Long requestedId : requestedIds) {
            RegressionTestSet testSet =
                    byId.get(requestedId);

            if (testSet == null) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "REGRESSION_TEST_SET_NOT_FOUND",
                        "回归测试集不存在，ID="
                                + requestedId
                );
            }

            if (!"ACTIVE".equalsIgnoreCase(
                    testSet.getStatus()
            )) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "REGRESSION_TEST_SET_NOT_ACTIVE",
                        "只有ACTIVE状态的测试集可以运行，ID="
                                + requestedId
                );
            }

            ordered.add(testSet);
        }

        return ordered;
    }

    private Map<Long, List<RegressionTestCase>> loadEnabledCases(
            Collection<RegressionTestSet> testSets
    ) {
        Map<Long, List<RegressionTestCase>> result =
                new LinkedHashMap<>();

        for (RegressionTestSet testSet : testSets) {
            List<RegressionTestCase> cases =
                    testCaseMapper.selectList(
                            new LambdaQueryWrapper<RegressionTestCase>()
                                    .eq(
                                            RegressionTestCase::getTestSetId,
                                            testSet.getId()
                                    )
                                    .eq(
                                            RegressionTestCase::getEnabled,
                                            true
                                    )
                                    .orderByAsc(
                                            RegressionTestCase::getSequenceNo
                                    )
                                    .orderByAsc(
                                            RegressionTestCase::getId
                                    )
                    );

            result.put(
                    testSet.getId(),
                    cases
            );
        }

        return result;
    }

    private Map<Long, RegressionTestCaseResult> loadBaselineResults(
            Long baselineRunId
    ) {
        if (baselineRunId == null) {
            return Map.of();
        }

        RegressionTestRun baseline =
                persistenceService.getRun(
                        baselineRunId
                );

        if (!TERMINAL_BASELINE_STATUSES.contains(
                baseline.getStatus()
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REGRESSION_BASELINE_NOT_COMPLETED",
                    "基线运行尚未结束，不能用于结果比较"
            );
        }

        List<RegressionTestCaseResult> results =
                caseResultMapper.selectList(
                        new LambdaQueryWrapper<RegressionTestCaseResult>()
                                .eq(
                                        RegressionTestCaseResult::getRunId,
                                        baselineRunId
                                )
                );

        Map<Long, RegressionTestCaseResult> byCaseId =
                new LinkedHashMap<>();

        for (RegressionTestCaseResult result : results) {
            byCaseId.put(
                    result.getCaseId(),
                    result
            );
        }

        return byCaseId;
    }

    private void validateExecutors(
            Collection<RegressionTestSet> testSets
    ) {
        for (RegressionTestSet testSet : testSets) {
            String testType =
                    normalizeTestType(
                            testSet.getTestType()
                    );

            if (!executorRegistry.containsKey(
                    testType
            )) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "REGRESSION_EXECUTOR_NOT_FOUND",
                        "未找到测试类型对应的执行器："
                                + testType
                );
            }
        }
    }

    private Map<String, RegressionTestCaseExecutor>
    buildExecutorRegistry(
            List<RegressionTestCaseExecutor> executors
    ) {
        Map<String, RegressionTestCaseExecutor> result =
                new LinkedHashMap<>();

        if (executors == null) {
            return result;
        }

        for (RegressionTestCaseExecutor executor :
                executors) {

            if (executor == null) {
                continue;
            }

            String testType =
                    normalizeTestType(
                            executor.supportedTestType()
                    );

            if (result.put(
                    testType,
                    executor
            ) != null) {
                throw new IllegalStateException(
                        "存在重复的回归测试执行器："
                                + testType
                );
            }
        }

        return result;
    }

    private void validateRequest(
            RegressionTestRunRequest request
    ) {
        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REGRESSION_RUN_REQUEST_REQUIRED",
                    "回归测试运行请求不能为空"
            );
        }

        if (request.testSetIds() == null
                || request.testSetIds().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REGRESSION_TEST_SET_REQUIRED",
                    "至少选择一个回归测试集"
            );
        }
    }

    private List<Long> distinctTestSetIds(
            List<Long> source
    ) {
        LinkedHashSet<Long> unique =
                new LinkedHashSet<>();

        for (Long id : source) {
            if (id == null || id <= 0) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REGRESSION_TEST_SET_ID",
                        "回归测试集ID必须为正整数"
                );
            }

            if (!unique.add(id)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "DUPLICATE_REGRESSION_TEST_SET_ID",
                        "不能重复选择同一个回归测试集"
                );
            }
        }

        return new ArrayList<>(unique);
    }

    private String normalizeTestType(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "回归测试类型不能为空"
            );
        }

        return value.trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeAssertionStatus(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "断言状态不能为空"
            );
        }

        String normalized =
                value.trim()
                        .toUpperCase(Locale.ROOT);

        if (!ASSERTION_STATUSES.contains(
                normalized
        )) {
            throw new IllegalStateException(
                    "不支持的断言状态："
                            + normalized
            );
        }

        return normalized;
    }

    private BigDecimal progressPercent(
            int completed,
            Integer requested
    ) {
        int total =
                requested == null
                        ? 0
                        : requested;

        if (total <= 0) {
            return BigDecimal.valueOf(100)
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return BigDecimal.valueOf(completed)
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        BigDecimal.valueOf(total),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal overallProgressPercent(
            int completedCases,
            int requestedCases,
            int completedSets,
            int requestedSets
    ) {
        if (requestedCases > 0) {
            return progressPercent(
                    completedCases,
                    requestedCases
            );
        }

        return progressPercent(
                completedSets,
                requestedSets
        );
    }

    private void appendObservedVersions(
            RegressionTestRun run,
            List<RegressionTestCaseResult> completedResults
    ) {
        if (run == null
                || completedResults == null
                || completedResults.isEmpty()) {
            return;
        }

        Map<String, LinkedHashSet<String>>
                modelNamesByType =
                new LinkedHashMap<>();

        Map<String, LinkedHashSet<String>>
                modelVersionsByType =
                new LinkedHashMap<>();

        Map<String, LinkedHashSet<String>>
                promptVersionsByType =
                new LinkedHashMap<>();

        for (RegressionTestCaseResult result :
                completedResults) {

            if (result == null
                    || result.getTestType() == null
                    || result.getTestType().isBlank()) {
                continue;
            }

            String testType =
                    normalizeTestType(
                            result.getTestType()
                    );

            collectObservedValue(
                    modelNamesByType,
                    testType,
                    result.getModelName(),
                    "UNBOUND"
            );

            collectObservedValue(
                    modelVersionsByType,
                    testType,
                    result.getModelVersion(),
                    "UNBOUND"
            );

            collectObservedValue(
                    promptVersionsByType,
                    testType,
                    result.getPromptVersion(),
                    "DEFAULT_CODE_PROMPT"
            );
        }

        ObjectNode modelRoot =
                readVersionObject(
                        run.getModelVersions()
                );

        Set<String> modelTestTypes =
                new LinkedHashSet<>();

        modelTestTypes.addAll(
                modelNamesByType.keySet()
        );

        modelTestTypes.addAll(
                modelVersionsByType.keySet()
        );

        for (String testType : modelTestTypes) {
            ObjectNode item =
                    getOrCreateObject(
                            modelRoot,
                            testType
                    );

            Set<String> modelNames =
                    modelNamesByType.get(
                            testType
                    );

            Set<String> modelVersions =
                    modelVersionsByType.get(
                            testType
                    );

            writeObservedValues(
                    item,
                    "observedModelName",
                    "observedModelNames",
                    modelNames
            );

            writeObservedValues(
                    item,
                    "observedModelVersion",
                    "observedModelVersions",
                    modelVersions
            );

            item.put(
                    "observedStatus",
                    observedStatus(
                            modelNames,
                            modelVersions
                    )
            );
        }

        run.setModelVersions(
                modelRoot.toString()
        );

        ObjectNode promptRoot =
                readVersionObject(
                        run.getPromptVersions()
                );

        for (Map.Entry<String, LinkedHashSet<String>> entry :
                promptVersionsByType.entrySet()) {

            ObjectNode item =
                    getOrCreateObject(
                            promptRoot,
                            entry.getKey()
                    );

            writeObservedValues(
                    item,
                    "observedPromptVersion",
                    "observedPromptVersions",
                    entry.getValue()
            );

            item.put(
                    "observedStatus",
                    observedStatus(
                            entry.getValue(),
                            null
                    )
            );
        }

        run.setPromptVersions(
                promptRoot.toString()
        );
    }

    private void collectObservedValue(
            Map<String, LinkedHashSet<String>> target,
            String testType,
            String rawValue,
            String ignoredValue
    ) {
        if (rawValue == null
                || rawValue.isBlank()) {
            return;
        }

        String value =
                rawValue.trim();

        if (ignoredValue != null
                && ignoredValue.equalsIgnoreCase(
                        value
                )) {
            return;
        }

        target.computeIfAbsent(
                testType,
                key -> new LinkedHashSet<>()
        ).add(value);
    }

    private void writeObservedValues(
            ObjectNode item,
            String singularField,
            String pluralField,
            Set<String> values
    ) {
        ArrayNode array =
                item.putArray(pluralField);

        if (values != null) {
            for (String value : values) {
                array.add(value);
            }
        }

        if (values == null || values.isEmpty()) {
            item.putNull(singularField);
            return;
        }

        if (values.size() == 1) {
            item.put(
                    singularField,
                    values.iterator().next()
            );
            return;
        }

        item.put(
                singularField,
                "MIXED"
        );
    }

    private String observedStatus(
            Set<String> firstValues,
            Set<String> secondValues
    ) {
        boolean firstEmpty =
                firstValues == null
                        || firstValues.isEmpty();

        boolean secondEmpty =
                secondValues == null
                        || secondValues.isEmpty();

        if (firstEmpty && secondEmpty) {
            return "NONE";
        }

        if ((!firstEmpty
                && firstValues.size() > 1)
                || (!secondEmpty
                && secondValues.size() > 1)) {
            return "MIXED";
        }

        return "SINGLE";
    }

    private ObjectNode getOrCreateObject(
            ObjectNode root,
            String fieldName
    ) {
        JsonNode existing =
                root.get(fieldName);

        if (existing != null
                && existing.isObject()) {
            return (ObjectNode) existing;
        }

        return root.putObject(fieldName);
    }

    private ObjectNode readVersionObject(
            String rawJson
    ) {
        if (rawJson == null
                || rawJson.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode value =
                    objectMapper.readTree(rawJson);

            if (value != null
                    && value.isObject()) {
                return (ObjectNode) value;
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "回归测试版本快照不是合法JSON",
                    exception
            );
        }

        throw new IllegalStateException(
                "回归测试版本快照必须为JSON对象"
        );
    }

    private String buildModelVersionsJson(
            Map<String, RegressionVersionSnapshot> snapshots
    ) {
        ObjectNode root =
                objectMapper.createObjectNode();

        for (Map.Entry<String, RegressionVersionSnapshot> entry :
                snapshots.entrySet()) {

            RegressionVersionSnapshot snapshot =
                    entry.getValue();

            ObjectNode item =
                    root.putObject(
                            entry.getKey()
                    );

            putNullable(
                    item,
                    "modelName",
                    snapshot.modelName()
            );

            putNullable(
                    item,
                    "modelVersion",
                    snapshot.modelVersion()
            );

            if (snapshot.modelConfigId() == null) {
                item.putNull("modelConfigId");
            } else {
                item.put(
                        "modelConfigId",
                        snapshot.modelConfigId()
                );
            }

            putNullable(
                    item,
                    "modelConfigName",
                    snapshot.modelConfigName()
            );
        }

        return root.toString();
    }

    private String buildPromptVersionsJson(
            Map<String, RegressionVersionSnapshot> snapshots
    ) {
        ObjectNode root =
                objectMapper.createObjectNode();

        for (Map.Entry<String, RegressionVersionSnapshot> entry :
                snapshots.entrySet()) {

            RegressionVersionSnapshot snapshot =
                    entry.getValue();

            ObjectNode item =
                    root.putObject(
                            entry.getKey()
                    );

            putNullable(
                    item,
                    "promptVersion",
                    snapshot.promptVersion()
            );

            if (snapshot.promptDefinitionId() == null) {
                item.putNull("promptDefinitionId");
            } else {
                item.put(
                        "promptDefinitionId",
                        snapshot.promptDefinitionId()
                );
            }

            if (snapshot.promptVersionId() == null) {
                item.putNull("promptVersionId");
            } else {
                item.put(
                        "promptVersionId",
                        snapshot.promptVersionId()
                );
            }
        }

        return root.toString();
    }

    private String buildAlgorithmVersionsJson(
            Map<String, RegressionVersionSnapshot> snapshots
    ) {
        ObjectNode root =
                objectMapper.createObjectNode();

        for (Map.Entry<String, RegressionVersionSnapshot> entry :
                snapshots.entrySet()) {

            root.put(
                    entry.getKey(),
                    entry.getValue()
                            .algorithmVersion()
            );
        }

        return root.toString();
    }

    private String buildDataVersionsJson(
            List<RegressionTestSet> testSets
    ) {
        ObjectNode root =
                objectMapper.createObjectNode();

        for (RegressionTestSet testSet : testSets) {
            putNullable(
                    root,
                    String.valueOf(
                            testSet.getId()
                    ),
                    testSet.getDataVersion()
            );
        }

        return root.toString();
    }

    private void putNullable(
            ObjectNode target,
            String fieldName,
            String value
    ) {
        if (value == null) {
            target.putNull(fieldName);
        } else {
            target.put(
                    fieldName,
                    value
            );
        }
    }

    private String executionFailureReasons(
            Exception exception
    ) {
        ArrayNode reasons =
                objectMapper.createArrayNode();

        ObjectNode reason =
                reasons.addObject();

        reason.put(
                "type",
                "EXECUTION_ERROR"
        );

        reason.put(
                "message",
                errorMessage(exception)
        );

        reason.put(
                "exceptionClass",
                exception.getClass()
                        .getSimpleName()
        );

        return reasons.toString();
    }

    private String jsonOrDefault(
            JsonNode value,
            String defaultValue
    ) {
        return value == null
                || value.isNull()
                ? defaultValue
                : value.toString();
    }

    private String jsonArrayOrDefault(
            JsonNode value
    ) {
        if (value == null || value.isNull()) {
            return "[]";
        }

        if (!value.isArray()) {
            ArrayNode wrapped =
                    objectMapper.createArrayNode();

            wrapped.add(
                    value.deepCopy()
            );

            return wrapped.toString();
        }

        return value.toString();
    }

    private String defaultJsonObject(
            String value
    ) {
        return value == null
                || value.isBlank()
                ? "{}"
                : value;
    }

    private String writeJson(
            Object value
    ) {
        try {
            return objectMapper
                    .writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "回归测试请求快照序列化失败",
                    exception
            );
        }
    }

    private JsonNode readJsonOrDefault(
            String value,
            String defaultValue
    ) {
        try {
            return objectMapper.readTree(
                    value == null
                            || value.isBlank()
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

    private String cleanRunName(
            String value,
            OffsetDateTime now
    ) {
        if (value == null || value.isBlank()) {
            return "Regression Run "
                    + now;
        }

        return value.trim();
    }

    private String textOrDefault(
            String value,
            String defaultValue
    ) {
        return value == null
                || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private long elapsedMilliseconds(
            long startedNanos
    ) {
        return Math.max(
                0L,
                (
                        System.nanoTime()
                                - startedNanos
                ) / 1_000_000L
        );
    }

    private String errorMessage(
            Exception exception
    ) {
        String message =
                exception.getMessage();

        String result =
                message == null
                        || message.isBlank()
                        ? exception.getClass()
                                .getSimpleName()
                        : exception.getClass()
                                .getSimpleName()
                                + ": "
                                + message;

        return result.length() <= 2000
                ? result
                : result.substring(
                        0,
                        2000
                );
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

    private static final class SetCounters {

        private int completedCaseCount;

        private int passedCount;

        private int assertionFailedCount;

        private int executionErrorCount;
    }

    private static final class RunCounters {

        private int completedSetCount;

        private int completedCaseCount;

        private int passedCount;

        private int assertionFailedCount;

        private int executionErrorCount;
    }
}