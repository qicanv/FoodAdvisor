package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.report.MyReportListVO;
import com.foodadvisor.dto.report.ReviewReportRequest;
import com.foodadvisor.entity.ReviewReport;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.ReviewReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 评价举报接口（EPIC-08 故事5）
 */
@RestController
@RequestMapping("/api/reports")
public class ReviewReportController {

    private final ReviewReportService reportService;

    public ReviewReportController(ReviewReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 提交举报
     */
    @PostMapping
    public ApiResponse<ReviewReport> submit(
            @RequestBody ReviewReportRequest request,
            HttpServletRequest servletRequest
    ) {
        Long userId = requireUserId(servletRequest);
        ReviewReport report = reportService.submitReport(userId, request);
        return ApiResponse.success("举报已提交", report);
    }

    /**
     * 查询当前用户的举报列表（"我的举报"页面）
     */
    @GetMapping("/my")
    public ApiResponse<PageResult<MyReportListVO>> myReports(
            HttpServletRequest servletRequest,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status
    ) {
        Long userId = requireUserId(servletRequest);
        Page<MyReportListVO> page = reportService.listMyReports(
                userId, pageNum, pageSize, status);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 获取举报详情
     */
    @GetMapping("/{reportId}")
    public ApiResponse<MyReportListVO> detail(
            @PathVariable Long reportId,
            HttpServletRequest servletRequest
    ) {
        Long userId = requireUserId(servletRequest);
        MyReportListVO detail = reportService.getReportDetail(userId, reportId);
        return ApiResponse.success(detail);
    }

    private Long requireUserId(HttpServletRequest request) {
        // 优先从 X-User-Id 请求头获取
        String headerUserId = request.getHeader("X-User-Id");
        if (headerUserId != null && !headerUserId.isBlank()) {
            try {
                return Long.parseLong(headerUserId);
            } catch (NumberFormatException ignored) {
                // fall through to attribute check
            }
        }

        // 其次从 request attribute 获取（由 Filter/Interceptor 设置）
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
