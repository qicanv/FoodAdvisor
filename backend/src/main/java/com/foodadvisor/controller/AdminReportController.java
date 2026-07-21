package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.report.AdminReportListVO;
import com.foodadvisor.dto.report.ResolveReportRequest;
import com.foodadvisor.entity.ReviewReport;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.ReviewReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员举报审核接口（EPIC-08 故事5 管理端）
 * 允许 ADMIN 和 OPERATOR 角色访问
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReviewReportService reportService;

    public AdminReportController(ReviewReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 管理员查看所有举报列表（支持筛选）
     */
    @GetMapping
    public ApiResponse<PageResult<AdminReportListVO>> list(
            HttpServletRequest servletRequest,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String reason
    ) {
        requireAdminOrOperator(servletRequest);
        Page<AdminReportListVO> page = reportService.listAllReports(
                status, merchantId, reason, pageNum, pageSize);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 管理员获取单条举报详情
     */
    @GetMapping("/{reportId}")
    public ApiResponse<AdminReportListVO> detail(
            @PathVariable Long reportId,
            HttpServletRequest servletRequest
    ) {
        requireAdminOrOperator(servletRequest);
        // 复用 listAllReports 的 VO 结构，通过过滤单条实现
        Page<AdminReportListVO> page = reportService.listAllReports(
                null, null, null, 1, 1000);
        return page.getRecords().stream()
                .filter(r -> r.getId().equals(reportId))
                .findFirst()
                .map(ApiResponse::success)
                .orElse(ApiResponse.notFound("举报记录不存在"));
    }

    /**
     * 管理员处理举报（通过/驳回）
     */
    @PutMapping("/{reportId}/resolve")
    public ApiResponse<ReviewReport> resolve(
            @PathVariable Long reportId,
            @RequestBody ResolveReportRequest request,
            HttpServletRequest servletRequest
    ) {
        Long operatorUserId = requireUserId(servletRequest);
        requireAdminOrOperator(servletRequest);
        ReviewReport report = reportService.resolveReport(
                reportId, request, operatorUserId);
        return ApiResponse.success("处理成功", report);
    }

    private void requireAdminOrOperator(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        String roleStr = role == null ? "" : role.toString().toUpperCase();
        if (!"ADMIN".equals(roleStr) && !"OPERATOR".equals(roleStr)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "仅管理员和运营人员可访问举报审核功能"
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
}
