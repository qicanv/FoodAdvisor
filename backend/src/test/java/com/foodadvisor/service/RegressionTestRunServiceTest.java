package com.foodadvisor.service;

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
import com.foodadvisor.mapper.RegressionTestCaseMapper;
import com.foodadvisor.mapper.RegressionTestCaseResultMapper;
import com.foodadvisor.mapper.RegressionTestSetMapper;
import com.foodadvisor.trace.AiTraceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegressionTestRunServiceTest {

    @Mock
    private RegressionTestSetMapper testSetMapper;

    @Mock
    private RegressionTestCaseMapper testCaseMapper;

    @Mock
    private RegressionTestCaseResultMapper caseResultMapper;

    @Mock
    private RegressionVersionSnapshotService versionSnapshotService;

    @Mock
    private RegressionTestRunPersistenceService persistenceService;

    @Mock
    private RegressionTestCaseExecutor executor;

    private ObjectMapper objectMapper;

    private RegressionTestRunService runService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        /*
         * RegressionTestRunService 构造时会立即建立执行器注册表，
         * 所以 supportedTestType 必须在构造服务前设置。
         */
        when(
                executor.supportedTestType()
        ).thenReturn(
                "CONSTRAINT_EXTRACTION"
        );

        runService =
                new RegressionTestRunService(
                        testSetMapper,
                        testCaseMapper,
                        caseResultMapper,
                        versionSnapshotService,
                        persistenceService,
                        objectMapper,
                        List.of(executor)
                );
    }

    @Test
    void shouldKeepCompletedProgressWhenOneCaseExecutionFails() {
        RegressionTestSet testSet =
                createTestSet();

        RegressionTestCase successfulCase =
                createTestCase(
                        101L,
                        "CASE-101",
                        "成功案例",
                        1
                );

        RegressionTestCase failedCase =
                createTestCase(
                        102L,
                        "CASE-102",
                        "执行失败案例",
                        2
                );

        RegressionVersionSnapshot snapshot =
                createVersionSnapshot();

        when(
                testSetMapper.selectBatchIds(
                        anyCollection()
                )
        ).thenReturn(
                List.of(testSet)
        );

        when(
                testCaseMapper.selectList(any())
        ).thenReturn(
                List.of(
                        successfulCase,
                        failedCase
                )
        );

        when(
                versionSnapshotService.resolveAll(any())
        ).thenReturn(
                Map.of(
                        "CONSTRAINT_EXTRACTION",
                        snapshot
                )
        );

        /*
         * 模拟数据库插入后生成运行ID和运行测试集ID。
         */
        when(
                persistenceService.createRunWithSets(
                        any(RegressionTestRun.class),
                        anyList()
                )
        ).thenAnswer(invocation -> {
            RegressionTestRun run =
                    invocation.getArgument(0);

            List<RegressionTestRunSet> runSets =
                    invocation.getArgument(1);

            run.setId(900L);

            long runSetId = 1000L;

            for (RegressionTestRunSet runSet : runSets) {
                runSet.setId(runSetId++);
                runSet.setRunId(run.getId());
            }

            return run;
        });

        AtomicLong resultIdSequence =
                new AtomicLong(2000L);

        when(
                persistenceService.createCaseResult(
                        any(RegressionTestCaseResult.class)
                )
        ).thenAnswer(invocation -> {
            RegressionTestCaseResult result =
                    invocation.getArgument(0);

            result.setId(
                    resultIdSequence.getAndIncrement()
            );

            return result;
        });

        when(
                executor.execute(
                        any(RegressionTestCase.class),
                        any(RegressionVersionSnapshot.class),
                        any(),
                        any(AiTraceContext.class)
                )
        ).thenAnswer(invocation -> {
            RegressionTestCase testCase =
                    invocation.getArgument(0);

            AiTraceContext traceContext =
                    invocation.getArgument(3);

            if (Long.valueOf(101L).equals(
                    testCase.getId()
            )) {
                ObjectNode actualOutput =
                        objectMapper.createObjectNode();

                actualOutput.put(
                        "cuisine",
                        "川菜"
                );

                ObjectNode metrics =
                        objectMapper.createObjectNode();

                metrics.put(
                        "accuracy",
                        1.0
                );

                ArrayNode failureReasons =
                        objectMapper.createArrayNode();

                return RegressionExecutionResult.passed(
                        actualOutput,
                        metrics,
                        failureReasons,
                        "test-model",
                        "model-v1",
                        "prompt-v1",
                        traceContext.traceId()
                );
            }

            throw new IllegalStateException(
                    "simulated executor failure"
            );
        });

        ObjectNode executionOptions =
                objectMapper.createObjectNode();

        executionOptions.put(
                "topK",
                10
        );

        RegressionTestRunRequest request =
                new RegressionTestRunRequest(
                        "批量回归失败保留进度测试",
                        List.of(10L),
                        null,
                        executionOptions
                );

        RegressionTestRunResponse response =
                runService.executeRun(
                        request,
                        88L
                );

        assertAll(
                () -> assertNotNull(
                        response.id()
                ),
                () -> assertEquals(
                        900L,
                        response.id()
                ),
                () -> assertEquals(
                        "PARTIAL",
                        response.status()
                ),
                () -> assertEquals(
                        1,
                        response.requestedSetCount()
                ),
                () -> assertEquals(
                        1,
                        response.completedSetCount()
                ),
                () -> assertEquals(
                        2,
                        response.requestedCaseCount()
                ),
                () -> assertEquals(
                        2,
                        response.completedCaseCount()
                ),
                () -> assertEquals(
                        1,
                        response.passedCount()
                ),
                () -> assertEquals(
                        0,
                        response.assertionFailedCount()
                ),
                () -> assertEquals(
                        1,
                        response.executionErrorCount()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("100.00")
                                .compareTo(
                                        response.progressPercent()
                                )
                ),
                () -> assertTrue(
                        response.errorMessage()
                                .contains("1")
                )
        );

        assertAll(
                () -> assertEquals(
                        "UNBOUND",
                        response.modelVersions()
                                .path("CONSTRAINT_EXTRACTION")
                                .path("modelName")
                                .asText()
                ),
                () -> assertEquals(
                        "test-model",
                        response.modelVersions()
                                .path("CONSTRAINT_EXTRACTION")
                                .path("observedModelName")
                                .asText()
                ),
                () -> assertEquals(
                        "model-v1",
                        response.modelVersions()
                                .path("CONSTRAINT_EXTRACTION")
                                .path("observedModelVersion")
                                .asText()
                ),
                () -> assertEquals(
                        "SINGLE",
                        response.modelVersions()
                                .path("CONSTRAINT_EXTRACTION")
                                .path("observedStatus")
                                .asText()
                ),
                () -> assertEquals(
                        "DEFAULT_CODE_PROMPT",
                        response.promptVersions()
                                .path("CONSTRAINT_EXTRACTION")
                                .path("promptVersion")
                                .asText()
                ),
                () -> assertEquals(
                        "prompt-v1",
                        response.promptVersions()
                                .path("CONSTRAINT_EXTRACTION")
                                .path("observedPromptVersion")
                                .asText()
                ),
                () -> assertEquals(
                        "SINGLE",
                        response.promptVersions()
                                .path("CONSTRAINT_EXTRACTION")
                                .path("observedStatus")
                                .asText()
                )
        );

        /*
         * 两个案例都应先创建运行中记录，再更新最终结果。
         */
        verify(
                persistenceService,
                times(2)
        ).createCaseResult(
                any(RegressionTestCaseResult.class)
        );

        ArgumentCaptor<RegressionTestCaseResult>
                caseResultCaptor =
                ArgumentCaptor.forClass(
                        RegressionTestCaseResult.class
                );

        verify(
                persistenceService,
                times(2)
        ).updateCaseResult(
                caseResultCaptor.capture()
        );

        Map<Long, RegressionTestCaseResult> resultsByCaseId =
                caseResultCaptor.getAllValues()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        RegressionTestCaseResult::getCaseId,
                                        Function.identity()
                                )
                        );

        RegressionTestCaseResult successfulResult =
                resultsByCaseId.get(101L);

        RegressionTestCaseResult failedResult =
                resultsByCaseId.get(102L);

        assertAll(
                () -> assertNotNull(
                        successfulResult
                ),
                () -> assertEquals(
                        "SUCCESS",
                        successfulResult
                                .getExecutionStatus()
                ),
                () -> assertEquals(
                        "PASSED",
                        successfulResult
                                .getAssertionStatus()
                ),
                () -> assertEquals(
                        "NOT_COMPARED",
                        successfulResult
                                .getComparisonStatus()
                ),
                () -> assertTrue(
                        successfulResult
                                .getActualOutput()
                                .contains("川菜")
                ),
                () -> assertNotNull(
                        successfulResult
                                .getCompletedAt()
                )
        );

        assertAll(
                () -> assertNotNull(
                        failedResult
                ),
                () -> assertEquals(
                        "FAILED",
                        failedResult
                                .getExecutionStatus()
                ),
                () -> assertEquals(
                        "NOT_EVALUATED",
                        failedResult
                                .getAssertionStatus()
                ),
                () -> assertEquals(
                        "NOT_COMPARED",
                        failedResult
                                .getComparisonStatus()
                ),
                () -> assertTrue(
                        failedResult
                                .getErrorMessage()
                                .contains(
                                        "simulated executor failure"
                                )
                ),
                () -> assertTrue(
                        failedResult
                                .getFailureReasons()
                                .contains(
                                        "EXECUTION_ERROR"
                                )
                ),
                () -> assertNotNull(
                        failedResult
                                .getCompletedAt()
                )
        );

        /*
         * 测试集任务本身也必须保存为 PARTIAL 和 100%。
         */
        ArgumentCaptor<RegressionTestRunSet>
                runSetCaptor =
                ArgumentCaptor.forClass(
                        RegressionTestRunSet.class
                );

        verify(
                persistenceService,
                atLeastOnce()
        ).updateRunSet(
                runSetCaptor.capture()
        );

        RegressionTestRunSet finalRunSet =
                runSetCaptor.getAllValues()
                        .get(
                                runSetCaptor
                                        .getAllValues()
                                        .size() - 1
                        );

        assertAll(
                () -> assertEquals(
                        "PARTIAL",
                        finalRunSet.getStatus()
                ),
                () -> assertEquals(
                        2,
                        finalRunSet
                                .getCompletedCaseCount()
                ),
                () -> assertEquals(
                        1,
                        finalRunSet.getPassedCount()
                ),
                () -> assertEquals(
                        0,
                        finalRunSet
                                .getAssertionFailedCount()
                ),
                () -> assertEquals(
                        1,
                        finalRunSet
                                .getExecutionErrorCount()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("100.00")
                                .compareTo(
                                        finalRunSet
                                                .getProgressPercent()
                                )
                ),
                () -> assertNotNull(
                        finalRunSet.getCompletedAt()
                )
        );

        /*
         * 运行汇总必须在案例处理过程中和最终结束时持续保存。
         */
        verify(
                persistenceService,
                atLeastOnce()
        ).updateRun(
                any(RegressionTestRun.class)
        );
    }

    private RegressionTestSet createTestSet() {
        RegressionTestSet testSet =
                new RegressionTestSet();

        testSet.setId(10L);
        testSet.setName(
                "条件提取标准测试集"
        );
        testSet.setTestType(
                "CONSTRAINT_EXTRACTION"
        );
        testSet.setDataVersion(
                "dataset-v1"
        );
        testSet.setStatus(
                "ACTIVE"
        );

        return testSet;
    }

    private RegressionTestCase createTestCase(
            Long id,
            String caseCode,
            String caseName,
            int sequenceNo
    ) {
        RegressionTestCase testCase =
                new RegressionTestCase();

        testCase.setId(id);
        testCase.setTestSetId(10L);
        testCase.setCaseCode(caseCode);
        testCase.setCaseName(caseName);
        testCase.setInputPayload(
                """
                {
                  "content": "四个人，人均八十，想吃川菜"
                }
                """
        );
        testCase.setExpectedOutput(
                """
                {
                  "cuisine": "川菜"
                }
                """
        );
        testCase.setAssertionConfig("{}");
        testCase.setTags("[]");
        testCase.setSequenceNo(sequenceNo);
        testCase.setEnabled(true);

        return testCase;
    }

    private RegressionVersionSnapshot
    createVersionSnapshot() {
        return new RegressionVersionSnapshot(
                "CONSTRAINT_EXTRACTION",
                "DINING_CONSTRAINT_EXTRACTION",

                null,
                null,
                "UNBOUND",
                "UNBOUND",
                null,

                null,
                null,
                "DEFAULT_CODE_PROMPT",

                "constraint-algorithm-v1"
        );
    }
}