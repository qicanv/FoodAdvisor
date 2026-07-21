package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.evaluation.RecommendationEvalAnnotationRequest;
import com.foodadvisor.dto.evaluation.RecommendationEvalRunComparisonResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalCaseRequest;
import com.foodadvisor.dto.evaluation.RecommendationEvalCaseResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalDatasetRequest;
import com.foodadvisor.dto.evaluation.RecommendationEvalDatasetResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalCaseResultResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalRunRequest;
import com.foodadvisor.dto.evaluation.RecommendationEvalRunResponse;
import com.foodadvisor.security.TraceAccessGuard;
import com.foodadvisor.service.RecommendationEvaluationDatasetService;
import com.foodadvisor.service.RecommendationEvaluationRunService;
import com.foodadvisor.util.AuthenticatedUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/recommendation-evaluations")
public class AdminRecommendationEvaluationController {

    private final RecommendationEvaluationDatasetService datasetService;
    private final TraceAccessGuard accessGuard;
    private final RecommendationEvaluationRunService runService;

    public AdminRecommendationEvaluationController(
            RecommendationEvaluationDatasetService datasetService,
            TraceAccessGuard accessGuard,
            RecommendationEvaluationRunService runService
    ) {
        this.datasetService = datasetService;
        this.accessGuard = accessGuard;
        this.runService = runService;
    }

    /**
     * 分页查询评测测试集。
     */
    @GetMapping("/datasets")
    public ApiResponse<PageResult<RecommendationEvalDatasetResponse>>
    listDatasets(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                PageResult.from(
                        datasetService.queryDatasets(
                                pageNum,
                                pageSize,
                                status,
                                keyword
                        )
                )
        );
    }

    /**
     * 查询测试集详情。
     */
    @GetMapping("/datasets/{datasetId}")
    public ApiResponse<RecommendationEvalDatasetResponse> getDataset(
            @PathVariable Long datasetId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.getDataset(datasetId)
        );
    }

    /**
     * 创建测试集。
     */
    @PostMapping("/datasets")
    public ApiResponse<RecommendationEvalDatasetResponse> createDataset(
            @Valid @RequestBody RecommendationEvalDatasetRequest body,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        Long userId = AuthenticatedUserId.require(request);

        return ApiResponse.success(
                datasetService.createDataset(body, userId)
        );
    }

    /**
     * 修改测试集。
     */
    @PutMapping("/datasets/{datasetId}")
    public ApiResponse<RecommendationEvalDatasetResponse> updateDataset(
            @PathVariable Long datasetId,
            @Valid @RequestBody RecommendationEvalDatasetRequest body,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.updateDataset(datasetId, body)
        );
    }

    /**
     * 查询测试集中的案例。
     */
    @GetMapping("/datasets/{datasetId}/cases")
    public ApiResponse<List<RecommendationEvalCaseResponse>> listCases(
            @PathVariable Long datasetId,
            @RequestParam(required = false) Boolean enabled,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.listCases(datasetId, enabled)
        );
    }

    /**
     * 查询单个测试案例。
     */
    @GetMapping("/datasets/{datasetId}/cases/{caseId}")
    public ApiResponse<RecommendationEvalCaseResponse> getCase(
            @PathVariable Long datasetId,
            @PathVariable Long caseId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.getCase(datasetId, caseId)
        );
    }

    /**
     * 向测试集添加案例。
     */
    @PostMapping("/datasets/{datasetId}/cases")
    public ApiResponse<RecommendationEvalCaseResponse> createCase(
            @PathVariable Long datasetId,
            @Valid @RequestBody RecommendationEvalCaseRequest body,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.createCase(datasetId, body)
        );
    }

    /**
     * 修改测试案例。
     */
    @PutMapping("/datasets/{datasetId}/cases/{caseId}")
    public ApiResponse<RecommendationEvalCaseResponse> updateCase(
            @PathVariable Long datasetId,
            @PathVariable Long caseId,
            @Valid @RequestBody RecommendationEvalCaseRequest body,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                datasetService.updateCase(datasetId, caseId, body)
        );
    }

    /**
     * 删除测试案例。
     */
    @DeleteMapping("/datasets/{datasetId}/cases/{caseId}")
    public ApiResponse<Boolean> deleteCase(
            @PathVariable Long datasetId,
            @PathVariable Long caseId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        datasetService.deleteCase(datasetId, caseId);

        return ApiResponse.success(Boolean.TRUE);
    }

    /**
     * 同步执行一次标准测试集评测。
     */
    @PostMapping("/datasets/{datasetId}/runs")
    public ApiResponse<RecommendationEvalRunResponse> executeRun(
            @PathVariable Long datasetId,
            @Valid
            @RequestBody(required = false)
            RecommendationEvalRunRequest runRequest,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        Long createdBy =
                AuthenticatedUserId.require(request);

        return ApiResponse.success(
                runService.executeRun(
                        datasetId,
                        runRequest,
                        createdBy
                )
        );
    }

    /**
     * 比较同一测试集的两次评测运行。
     */
    @GetMapping("/runs/compare")
    public ApiResponse<RecommendationEvalRunComparisonResponse>
    compareRuns(
            @RequestParam Long baselineRunId,
            @RequestParam Long candidateRunId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                runService.compareRuns(
                        baselineRunId,
                        candidateRunId
                )
        );
    }

    /**
     * 查询单次评测运行及其汇总指标。
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<RecommendationEvalRunResponse> getRun(
            @PathVariable Long runId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                runService.getRun(runId)
        );
    }

    /**
     * 查询一次评测运行中的全部案例结果。
     */
    @GetMapping("/runs/{runId}/results")
    public ApiResponse<List<RecommendationEvalCaseResultResponse>>
    getRunResults(
            @PathVariable Long runId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        return ApiResponse.success(
                runService.listResults(runId)
        );
    }

    /**
     * 人工标注单个评测案例结果。
     */
    @PutMapping("/runs/{runId}/results/{resultId}/annotation")
    public ApiResponse<RecommendationEvalCaseResultResponse>
    annotateResult(
            @PathVariable Long runId,
            @PathVariable Long resultId,
            @Valid
            @RequestBody
            RecommendationEvalAnnotationRequest body,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        Long annotatedBy =
                AuthenticatedUserId.require(request);

        return ApiResponse.success(
                runService.annotateResult(
                        runId,
                        resultId,
                        body,
                        annotatedBy
                )
        );
    }
}