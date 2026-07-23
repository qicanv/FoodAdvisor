package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.fraud.*;
import com.foodadvisor.entity.ReviewFraudCase;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.FraudCaseService;
import com.foodadvisor.service.FraudDetectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 刷评检测接口（EPIC-03 故事4）
 * 允许 ADMIN 和 OPERATOR 角色访问
 */
@RestController
@RequestMapping("/api/admin/fraud-detection")
public class FraudDetectionController {

    private final FraudDetectionService detectionService;
    private final FraudCaseService fraudCaseService;

    public FraudDetectionController(FraudDetectionService detectionService,
                                     FraudCaseService fraudCaseService) {
        this.detectionService = detectionService;
        this.fraudCaseService = fraudCaseService;
    }

    /**
     * 手动触发检测扫描
     */
    @PostMapping("/scan")
    public ApiResponse<DetectResponse> scan(
            @RequestBody(required = false) DetectRequest request,
            HttpServletRequest servletRequest) {
        requireAdminOrOperator(servletRequest); //验证身份

        if (request == null) {
            request = new DetectRequest();
        }

        DetectResponse result = detectionService.scan(request);
        return ApiResponse.success("检测完成", result);
    }

    /**
     * 刷评案例列表
     */
    @GetMapping("/cases")
    public ApiResponse<PageResult<FraudCaseListVO>> listCases(
            HttpServletRequest servletRequest,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime) {
        requireAdminOrOperator(servletRequest);

        Page<FraudCaseListVO> page = fraudCaseService.listCases(
                status, riskLevel, ruleType, merchantId,
                startTime, endTime, pageNum, pageSize);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 刷评案例详情
     */
    @GetMapping("/cases/{caseId}")
    public ApiResponse<FraudCaseDetailVO> getCaseDetail(
            @PathVariable Long caseId,
            HttpServletRequest servletRequest) {
        requireAdminOrOperator(servletRequest);

        FraudCaseDetailVO detail = fraudCaseService.getCaseDetail(caseId);
        if (detail == null) {
            return ApiResponse.notFound("案例不存在");
        }
        return ApiResponse.success(detail);
    }

    /**
     * 提交人工复核
     */
    @PostMapping("/cases/{caseId}/review")
    public ApiResponse<?> submitReview(
            @PathVariable Long caseId,
            @Valid @RequestBody ReviewRequest request,
            HttpServletRequest servletRequest) {
        Long operatorUserId = requireUserId(servletRequest);
        String operatorUsername = getUsername(servletRequest);
        requireAdminOrOperator(servletRequest);

        ReviewFraudCase result = fraudCaseService.submitReview(
                caseId, request, operatorUserId, operatorUsername);

        return ApiResponse.success("复核完成", Map.of(
                "caseId", result.getId(),
                "status", result.getStatus(),
                "reviewedBy", result.getReviewedBy(),
                "reviewedByName", operatorUsername,
                "reviewedAt", result.getReviewedAt() != null
                        ? result.getReviewedAt().toString() : null,
                "conclusion", result.getReviewConclusion(),
                "remark", result.getReviewRemark() != null ? result.getReviewRemark() : ""
        ));
    }

    // ========== 权限校验 ==========

    private void requireAdminOrOperator(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        String roleStr = role == null ? "" : role.toString().toUpperCase();
        if (!"ADMIN".equals(roleStr) && !"OPERATOR".equals(roleStr)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "仅管理员和运营人员可访问刷评检测功能"
            );
        }
    }

    private Long requireUserId(HttpServletRequest request) {
        String headerUserId = request.getHeader("X-User-Id");
        if (headerUserId != null && !headerUserId.isBlank()) {
            try {
                return Long.parseLong(headerUserId);
            } catch (NumberFormatException ignored) {
            }
        }
        Object userId = request.getAttribute("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "请先登录后再操作"
        );
    }

    private String getUsername(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return username != null ? username.toString() : null;
    }
}
