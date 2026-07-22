package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.regression.RegressionTestCaseRequest;
import com.foodadvisor.dto.regression.RegressionTestCaseResponse;
import com.foodadvisor.dto.regression.RegressionTestSetRequest;
import com.foodadvisor.dto.regression.RegressionTestSetResponse;
import com.foodadvisor.dto.regression.RegressionTestCaseResultResponse;
import com.foodadvisor.dto.regression.RegressionTestRunComparisonResponse;
import com.foodadvisor.dto.regression.RegressionTestRunDetailResponse;
import com.foodadvisor.dto.regression.RegressionTestRunRequest;
import com.foodadvisor.dto.regression.RegressionTestRunResponse;
import com.foodadvisor.security.TraceAccessGuard;
import com.foodadvisor.service.RegressionTestDatasetService;
import com.foodadvisor.service.RegressionTestRunQueryService;
import com.foodadvisor.service.RegressionTestRunService;
import com.foodadvisor.util.AuthenticatedUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/regression-tests")
public class AdminRegressionTestController {

    private final RegressionTestDatasetService datasetService;

    private final RegressionTestRunService runService;

    private final RegressionTestRunQueryService runQueryService;

    private final TraceAccessGuard accessGuard;

    public AdminRegressionTestController(
            RegressionTestDatasetService datasetService,
            RegressionTestRunService runService,
            RegressionTestRunQueryService runQueryService,
            TraceAccessGuard accessGuard
    ) {
        this.datasetService = datasetService;
        this.runService = runService;
        this.runQueryService = runQueryService;
        this.accessGuard = accessGuard;
    }

    /**
     * 分页查询标准测试集。
     */
    @GetMapping("/sets")
    public ApiResponse<PageResult<RegressionTestSetResponse>>
    listTestSets(
            @RequestParam(defaultValue = "1")
            long pageNum,

            @RequestParam(defaultValue = "20")
            long pageSize,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String testType,

            @RequestParam(required = false)
            String keyword,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                PageResult.from(
                        datasetService.queryTestSets(
                                pageNum,
                                pageSize,
                                status,
                                testType,
                                keyword
                        )
                )
        );
    }

    /**
     * 查询标准测试集详情。
     */
    @GetMapping("/sets/{testSetId}")
    public ApiResponse<RegressionTestSetResponse>
    getTestSet(
            @PathVariable Long testSetId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.getTestSet(testSetId)
        );
    }

    /**
     * 创建标准测试集。
     */
    @PostMapping("/sets")
    public ApiResponse<RegressionTestSetResponse>
    createTestSet(
            @Valid
            @RequestBody
            RegressionTestSetRequest body,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        Long createdBy =
                AuthenticatedUserId.require(request);

        return ApiResponse.success(
                datasetService.createTestSet(
                        body,
                        createdBy
                )
        );
    }

    /**
     * 修改标准测试集。
     */
    @PutMapping("/sets/{testSetId}")
    public ApiResponse<RegressionTestSetResponse>
    updateTestSet(
            @PathVariable Long testSetId,

            @Valid
            @RequestBody
            RegressionTestSetRequest body,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.updateTestSet(
                        testSetId,
                        body
                )
        );
    }

    /**
     * 查询测试集下的案例。
     */
    @GetMapping("/sets/{testSetId}/cases")
    public ApiResponse<List<RegressionTestCaseResponse>>
    listCases(
            @PathVariable Long testSetId,

            @RequestParam(required = false)
            Boolean enabled,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.listCases(
                        testSetId,
                        enabled
                )
        );
    }

    /**
     * 查询单个测试案例。
     */
    @GetMapping("/sets/{testSetId}/cases/{caseId}")
    public ApiResponse<RegressionTestCaseResponse>
    getCase(
            @PathVariable Long testSetId,
            @PathVariable Long caseId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.getCase(
                        testSetId,
                        caseId
                )
        );
    }

    /**
     * 新建测试案例。
     */
    @PostMapping("/sets/{testSetId}/cases")
    public ApiResponse<RegressionTestCaseResponse>
    createCase(
            @PathVariable Long testSetId,

            @Valid
            @RequestBody
            RegressionTestCaseRequest body,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.createCase(
                        testSetId,
                        body
                )
        );
    }

    /**
     * 修改测试案例。
     */
    @PutMapping("/sets/{testSetId}/cases/{caseId}")
    public ApiResponse<RegressionTestCaseResponse>
    updateCase(
            @PathVariable Long testSetId,
            @PathVariable Long caseId,

            @Valid
            @RequestBody
            RegressionTestCaseRequest body,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.updateCase(
                        testSetId,
                        caseId,
                        body
                )
        );
    }

    /**
     * 删除测试案例。
     */
    @DeleteMapping("/sets/{testSetId}/cases/{caseId}")
    public ApiResponse<Boolean>
    deleteCase(
            @PathVariable Long testSetId,
            @PathVariable Long caseId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        datasetService.deleteCase(
                testSetId,
                caseId
        );

        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * 分页查询历史回归测试运行。
     */
    @GetMapping("/runs")
    public ApiResponse<PageResult<RegressionTestRunResponse>>
    listRuns(
            @RequestParam(defaultValue = "1")
            long pageNum,

            @RequestParam(defaultValue = "20")
            long pageSize,

            @RequestParam(required = false)
            String status,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                PageResult.from(
                        runQueryService.queryRuns(
                                pageNum,
                                pageSize,
                                status
                        )
                )
        );
    }

    /**
     * 同步执行一次批量回归测试。
     *
     * 一次可以选择一个或多个标准测试集。
     */
    @PostMapping("/runs")
    public ApiResponse<RegressionTestRunResponse>
    executeRun(
            @Valid
            @RequestBody
            RegressionTestRunRequest body,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        Long createdBy =
                AuthenticatedUserId.require(request);

        return ApiResponse.success(
                runService.executeRun(
                        body,
                        createdBy
                )
        );
    }

    /**
     * 比较两次已经结束的回归测试运行。
     */
    @GetMapping("/runs/compare")
    public ApiResponse<RegressionTestRunComparisonResponse>
    compareRuns(
            @RequestParam Long baselineRunId,
            @RequestParam Long candidateRunId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                runQueryService.compareRuns(
                        baselineRunId,
                        candidateRunId
                )
        );
    }

    /**
     * 查询一次回归测试运行的完整详情。
     *
     * 返回运行汇总、测试集进度和全部案例结果。
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<RegressionTestRunDetailResponse>
    getRunDetail(
            @PathVariable Long runId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                runQueryService.getRunDetail(runId)
        );
    }

    /**
     * 查询一次运行中的案例结果。
     *
     * 可以按执行状态、断言状态和比较状态过滤。
     */
    @GetMapping("/runs/{runId}/results")
    public ApiResponse<List<RegressionTestCaseResultResponse>>
    listRunResults(
            @PathVariable Long runId,

            @RequestParam(required = false)
            String executionStatus,

            @RequestParam(required = false)
            String assertionStatus,

            @RequestParam(required = false)
            String comparisonStatus,

            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                runQueryService.listResults(
                        runId,
                        executionStatus,
                        assertionStatus,
                        comparisonStatus
                )
        );
    }
}