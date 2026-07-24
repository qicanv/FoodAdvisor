package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.entity.User;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.ModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService moderationService;
    private final AdminAccessGuard adminAccessGuard;

    public ModerationController(ModerationService moderationService, AdminAccessGuard adminAccessGuard) {
        this.moderationService = moderationService;
        this.adminAccessGuard = adminAccessGuard;
    }

    @GetMapping("/reviews")
    public ApiResponse<Map<String, Object>> getReviewList(
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String moderationStatus,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        OffsetDateTime startOffset = startTime != null ? startTime.atOffset(java.time.ZoneOffset.UTC) : null;
        OffsetDateTime endOffset = endTime != null ? endTime.atOffset(java.time.ZoneOffset.UTC) : null;

        Map<String, Object> result = moderationService.getReviewList(
                riskType, riskLevel, moderationStatus, merchantId, startOffset, endOffset, pageNum, pageSize);

        return ApiResponse.success(result);
    }

    @GetMapping("/reviews/{id}")
    public ApiResponse<Map<String, Object>> getReviewDetail(
            @PathVariable Long id,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        Map<String, Object> detail = moderationService.getReviewDetail(id);

        if (detail == null) {
            return ApiResponse.notFound("评价不存在或已删除");
        }

        return ApiResponse.success(detail);
    }

    @GetMapping("/merchants")
    public ApiResponse<?> getActiveMerchants(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return ApiResponse.success(moderationService.getActiveMerchants());
    }

    @GetMapping("/pending-count")
    public ApiResponse<Long> getPendingCount(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return ApiResponse.success(moderationService.countPendingReviews());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        return ApiResponse.success(moderationService.getStats());
    }

    @PostMapping("/reviews/{id}/refresh-risk")
    public ApiResponse<?> refreshRiskDetection(
            @PathVariable Long id,
            HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        moderationService.refreshRiskDetection(id);
        return ApiResponse.success("风险检测已刷新");
    }

    @PostMapping("/reviews/refresh-all-risk")
    public ApiResponse<?> refreshAllRiskDetection(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        moderationService.refreshAllRiskDetection();
        return ApiResponse.success("所有待审核评价的风险检测已刷新");
    }

    /**
     * 为所有缺失风险类型的评价补充 risk_type。
     *
     * <p>使用关键词降级检测（不调用 AI），为每篇 review 确定
     * 风险类型（AD_SPAM / ABUSE / SPAM / OTHER），
     * 使内容审核工作台可以按风险类型筛选。</p>
     */
    @PostMapping("/reviews/backfill-risk-types")
    public ApiResponse<Map<String, Object>> backfillRiskTypes(HttpServletRequest request) {
        adminAccessGuard.requireAdmin(request);
        int updated = moderationService.backfillRiskTypes();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("updated", updated);
        result.put("message", "已为 " + updated + " 篇评价补充风险类型");
        return ApiResponse.success(result);
    }

    @PostMapping("/reviews/{id}/action")
    public ApiResponse<Map<String, Object>> moderateReview(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        adminAccessGuard.requireAdmin(request);

        String action = body.get("action");
        String remark = body.get("remark");

        if (action == null || action.isBlank()) {
            return ApiResponse.failure("INVALID_REQUEST", "审核操作不能为空");
        }

        Long operatorUserId = getOperatorUserId(request);
        String operatorUsername = getOperatorUsername(request);
        String operatorRole = getOperatorRole(request);

        Map<String, Object> result = moderationService.moderateReview(
                id, action, remark, operatorUserId, operatorUsername, operatorRole);

        if ((Boolean) result.get("success")) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.failure("MODERATION_FAILED", (String) result.get("message"));
        }
    }

    private Long getOperatorUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    private String getOperatorUsername(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return username != null ? username.toString() : null;
    }

    private String getOperatorRole(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        return role != null ? role.toString() : null;
    }
}