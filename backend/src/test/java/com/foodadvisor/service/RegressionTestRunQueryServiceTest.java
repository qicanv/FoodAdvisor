package com.foodadvisor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.regression.RegressionTestRunComparisonResponse;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.entity.RegressionTestCaseResult;
import com.foodadvisor.entity.RegressionTestRun;
import com.foodadvisor.mapper.RegressionTestCaseMapper;
import com.foodadvisor.mapper.RegressionTestCaseResultMapper;
import com.foodadvisor.mapper.RegressionTestRunMapper;
import com.foodadvisor.mapper.RegressionTestRunSetMapper;
import com.foodadvisor.mapper.RegressionTestSetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegressionTestRunQueryServiceTest {

    @Mock
    private RegressionTestRunMapper runMapper;

    @Mock
    private RegressionTestRunSetMapper runSetMapper;

    @Mock
    private RegressionTestCaseResultMapper caseResultMapper;

    @Mock
    private RegressionTestSetMapper testSetMapper;

    @Mock
    private RegressionTestCaseMapper testCaseMapper;

    @Mock
    private RegressionTestRunPersistenceService persistenceService;

    private RegressionTestRunQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService =
                new RegressionTestRunQueryService(
                        runMapper,
                        runSetMapper,
                        caseResultMapper,
                        testSetMapper,
                        testCaseMapper,
                        persistenceService,
                        new ObjectMapper()
                );
    }

    @Test
    void shouldCompareRunsAndProduceAllComparisonStatuses() {
        RegressionTestRun baselineRun =
                run(
                        1L,
                        "COMPLETED"
                );

        RegressionTestRun candidateRun =
                run(
                        2L,
                        "COMPLETED"
                );

        when(
                persistenceService.getRun(1L)
        ).thenReturn(baselineRun);

        when(
                persistenceService.getRun(2L)
        ).thenReturn(candidateRun);

        List<RegressionTestCaseResult> baselineResults =
                List.of(
                        result(
                                1001L,
                                1L,
                                101L,
                                "CASE-101",
                                "SUCCESS",
                                "PASSED"
                        ),
                        result(
                                1002L,
                                1L,
                                102L,
                                "CASE-102",
                                "SUCCESS",
                                "FAILED"
                        ),
                        result(
                                1003L,
                                1L,
                                103L,
                                "CASE-103",
                                "FAILED",
                                "NOT_EVALUATED"
                        ),
                        result(
                                1004L,
                                1L,
                                104L,
                                "CASE-104",
                                "SUCCESS",
                                "PASSED"
                        ),
                        result(
                                1006L,
                                1L,
                                106L,
                                "CASE-106",
                                "SUCCESS",
                                "PASSED"
                        )
                );

        List<RegressionTestCaseResult> candidateResults =
                List.of(
                        result(
                                2001L,
                                2L,
                                101L,
                                "CASE-101",
                                "SUCCESS",
                                "FAILED"
                        ),
                        result(
                                2002L,
                                2L,
                                102L,
                                "CASE-102",
                                "SUCCESS",
                                "PASSED"
                        ),
                        result(
                                2003L,
                                2L,
                                103L,
                                "CASE-103",
                                "FAILED",
                                "NOT_EVALUATED"
                        ),
                        result(
                                2004L,
                                2L,
                                104L,
                                "CASE-104",
                                "SUCCESS",
                                "PASSED"
                        ),
                        result(
                                2005L,
                                2L,
                                105L,
                                "CASE-105",
                                "SUCCESS",
                                "PASSED"
                        )
                );

        when(
                caseResultMapper.selectList(any())
        ).thenReturn(
                baselineResults,
                candidateResults
        );

        when(
                testCaseMapper.selectBatchIds(
                        anyCollection()
                )
        ).thenReturn(
                List.of(
                        testCase(
                                101L,
                                "CASE-101",
                                "新增失败案例"
                        ),
                        testCase(
                                102L,
                                "CASE-102",
                                "已修复案例"
                        ),
                        testCase(
                                103L,
                                "CASE-103",
                                "持续失败案例"
                        ),
                        testCase(
                                104L,
                                "CASE-104",
                                "持续通过案例"
                        ),
                        testCase(
                                105L,
                                "CASE-105",
                                "新增案例"
                        ),
                        testCase(
                                106L,
                                "CASE-106",
                                "移除案例"
                        )
                )
        );

        RegressionTestRunComparisonResponse response =
                queryService.compareRuns(
                        1L,
                        2L
                );

        assertAll(
                () -> assertEquals(
                        1L,
                        response.baselineRunId()
                ),
                () -> assertEquals(
                        2L,
                        response.candidateRunId()
                ),
                () -> assertEquals(
                        1,
                        response.summary()
                                .newFailureCount()
                ),
                () -> assertEquals(
                        1,
                        response.summary()
                                .fixedCount()
                ),
                () -> assertEquals(
                        1,
                        response.summary()
                                .stillFailedCount()
                ),
                () -> assertEquals(
                        1,
                        response.summary()
                                .stillPassedCount()
                ),
                () -> assertEquals(
                        1,
                        response.summary()
                                .newCaseCount()
                ),
                () -> assertEquals(
                        1,
                        response.summary()
                                .removedCaseCount()
                ),
                () -> assertEquals(
                        6,
                        response.cases().size()
                )
        );

        Map<Long, RegressionTestRunComparisonResponse.CaseComparison>
                comparisonsByCase =
                response.cases()
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        RegressionTestRunComparisonResponse
                                                .CaseComparison::caseId,
                                        Function.identity()
                                )
                        );

        assertAll(
                () -> assertEquals(
                        "NEW_FAILURE",
                        comparisonsByCase
                                .get(101L)
                                .comparisonStatus()
                ),
                () -> assertEquals(
                        "FIXED",
                        comparisonsByCase
                                .get(102L)
                                .comparisonStatus()
                ),
                () -> assertEquals(
                        "STILL_FAILED",
                        comparisonsByCase
                                .get(103L)
                                .comparisonStatus()
                ),
                () -> assertEquals(
                        "STILL_PASSED",
                        comparisonsByCase
                                .get(104L)
                                .comparisonStatus()
                ),
                () -> assertEquals(
                        "NEW_CASE",
                        comparisonsByCase
                                .get(105L)
                                .comparisonStatus()
                ),
                () -> assertEquals(
                        "REMOVED_CASE",
                        comparisonsByCase
                                .get(106L)
                                .comparisonStatus()
                )
        );

        RegressionTestRunComparisonResponse.CaseComparison
                newFailure =
                comparisonsByCase.get(101L);

        assertAll(
                () -> assertEquals(
                        "新增失败案例",
                        newFailure.caseName()
                ),
                () -> assertEquals(
                        "PASSED",
                        newFailure.baselineAssertionStatus()
                ),
                () -> assertEquals(
                        "FAILED",
                        newFailure.candidateAssertionStatus()
                ),
                () -> assertEquals(
                        1001L,
                        newFailure.baselineResultId()
                ),
                () -> assertEquals(
                        2001L,
                        newFailure.candidateResultId()
                )
        );

        RegressionTestRunComparisonResponse.CaseComparison
                removedCase =
                comparisonsByCase.get(106L);

        assertAll(
                () -> assertEquals(
                        "REMOVED_CASE",
                        removedCase.comparisonStatus()
                ),
                () -> assertEquals(
                        1006L,
                        removedCase.baselineResultId()
                ),
                () -> assertEquals(
                        null,
                        removedCase.candidateResultId()
                )
        );
    }

    private RegressionTestRun run(
            Long id,
            String status
    ) {
        RegressionTestRun run =
                new RegressionTestRun();

        run.setId(id);
        run.setStatus(status);

        return run;
    }

    private RegressionTestCaseResult result(
            Long id,
            Long runId,
            Long caseId,
            String caseCode,
            String executionStatus,
            String assertionStatus
    ) {
        RegressionTestCaseResult result =
                new RegressionTestCaseResult();

        result.setId(id);
        result.setRunId(runId);
        result.setCaseId(caseId);
        result.setCaseCodeSnapshot(caseCode);
        result.setTestType(
                "CONSTRAINT_EXTRACTION"
        );
        result.setExecutionStatus(
                executionStatus
        );
        result.setAssertionStatus(
                assertionStatus
        );

        return result;
    }

    private RegressionTestCase testCase(
            Long id,
            String caseCode,
            String caseName
    ) {
        RegressionTestCase testCase =
                new RegressionTestCase();

        testCase.setId(id);
        testCase.setCaseCode(caseCode);
        testCase.setCaseName(caseName);

        return testCase;
    }
}