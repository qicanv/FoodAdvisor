package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.feedback.AnalysisFeedbackStatisticsVO;
import com.foodadvisor.dto.feedback.AnalysisFeedbackSubmitRequest;
import com.foodadvisor.dto.feedback.AnalysisFeedbackVO;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.AnalysisFeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商家分析结果反馈接口（EPIC-06 Story 5）
 *
 * 提供商家端反馈提交/查询和管理员端统计/查询能力。
 *
 * 权限控制：
 * - /api/merchant-console/** 接口需要 JWT 认证 + 商家成员身份
 * - /api/admin/** 接口需要 ADMIN 或 OPERATOR 角色
 * - 商家只能提交和查看自己店铺的反馈（AC-5）
 *
 * 验收准则对齐：
 * - AC-1: 商家用户能够对属于自己店铺的分析结果提交准确或不准确反馈
 * - AC-2: 反馈保存对应 analysisId 和 merchantId
 * - AC-3: 支持填写并保存具体问题说明
 * - AC-4: 管理员能够按 AI 功能和反馈类型汇总数量
 * - AC-5: 商家用户查询其他店铺反馈时返回 403
 * - AC-6: 管理员能够从反馈列表查看对应分析记录和问题说明
 * - AC-7: 同一商家对同一分析记录重复反馈时更新已有记录
 */
@RestController
public class AnalysisFeedbackController {

    private static final Logger log =
            LoggerFactory.getLogger(AnalysisFeedbackController.class);

    private final AnalysisFeedbackService feedbackService;
    private final JdbcTemplate jdbcTemplate;

    public AnalysisFeedbackController(
            AnalysisFeedbackService feedbackService,
            JdbcTemplate jdbcTemplate
    ) {
        this.feedbackService = feedbackService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ============================================
    // 商家端：提交/更新反馈
    // ============================================

    /**
     * 商家端：提交或更新分析结果反馈。
     *
     * 同一商家对同一分析类型的同一分析记录，再次提交时更新原记录（AC-7）。
     * analysisId 不传或传 null 表示对该分析类型的整体反馈。
     *
     * 请求示例：
     *   POST /api/merchant-console/merchants/1/analysis-feedback
     *   Body: {
     *     "analysisType": "SENTIMENT",
     *     "analysisId": 101,
     *     "feedbackType": "ACCURATE",
     *     "content": "情感分析结果与实际一致"
     *   }
     *
     * @param merchantId 商家ID（路径参数）
     * @param request    反馈提交请求体
     * @param servletRequest HTTP 请求（用于获取当前用户）
     * @return 保存后的反馈记录
     */
    @PostMapping("/api/merchant-console/merchants/{merchantId}/analysis-feedback")
    public ApiResponse<AnalysisFeedbackVO> submitFeedback(
            @PathVariable Long merchantId,
            @RequestBody AnalysisFeedbackSubmitRequest request,
            HttpServletRequest servletRequest
    ) {
        Long userId = requireUserId(servletRequest);
        verifyMerchantMembership(userId, merchantId);

        log.info("商家用户提交分析反馈: userId={}, merchantId={}, analysisType={}, analysisId={}, feedbackType={}",
                userId, merchantId, request.getAnalysisType(), request.getAnalysisId(), request.getFeedbackType());

        AnalysisFeedbackVO result = feedbackService.submitFeedback(merchantId, userId, request);
        return ApiResponse.success("反馈提交成功", result);
    }

    /**
     * 商家端：查询自己店铺的分析反馈列表。
     *
     * 支持按分析类型和反馈类型筛选。
     *
     * 请求示例：
     *   GET /api/merchant-console/merchants/1/analysis-feedback?analysisType=SENTIMENT&feedbackType=ACCURATE&pageNum=1&pageSize=10
     *
     * @param merchantId   商家ID（路径参数）
     * @param analysisType 可选，按分析类型筛选
     * @param feedbackType 可选，按反馈类型筛选
     * @param pageNum      页码，默认 1
     * @param pageSize     每页条数，默认 20
     * @return 分页反馈列表
     */
    @GetMapping("/api/merchant-console/merchants/{merchantId}/analysis-feedback")
    public ApiResponse<PageResult<AnalysisFeedbackVO>> listMerchantFeedback(
            @PathVariable Long merchantId,
            @RequestParam(required = false) String analysisType,
            @RequestParam(required = false) String feedbackType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest servletRequest
    ) {
        Long userId = requireUserId(servletRequest);
        verifyMerchantMembership(userId, merchantId);

        PageResult<AnalysisFeedbackVO> result = feedbackService.getMerchantFeedback(
                merchantId, analysisType, feedbackType, pageNum, pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 商家端：查询单条反馈详情。
     *
     * 会校验反馈记录是否属于当前商家（AC-5）。
     *
     * 请求示例：
     *   GET /api/merchant-console/merchants/1/analysis-feedback/5
     */
    @GetMapping("/api/merchant-console/merchants/{merchantId}/analysis-feedback/{feedbackId}")
    public ApiResponse<AnalysisFeedbackVO> getFeedbackDetail(
            @PathVariable Long merchantId,
            @PathVariable Long feedbackId,
            HttpServletRequest servletRequest
    ) {
        Long userId = requireUserId(servletRequest);
        verifyMerchantMembership(userId, merchantId);

        AnalysisFeedbackVO result = feedbackService.getFeedbackDetail(feedbackId, merchantId);
        return ApiResponse.success(result);
    }

    // ============================================
    // 管理员端：统计与列表
    // ============================================

    /**
     * 管理员端：按分析类型和反馈类型汇总统计（AC-4）。
     *
     * 请求示例：
     *   GET /api/admin/analysis-feedback/statistics
     *
     * @return 反馈统计数据
     */
    @GetMapping("/api/admin/analysis-feedback/statistics")
    public ApiResponse<AnalysisFeedbackStatisticsVO> getStatistics(
            HttpServletRequest servletRequest
    ) {
        requireAdminOrOperator(servletRequest);

        AnalysisFeedbackStatisticsVO statistics = feedbackService.getStatistics();
        return ApiResponse.success(statistics);
    }

    /**
     * 管理员端：查询所有反馈列表（AC-6）。
     *
     * 支持按分析类型、反馈类型和商家筛选。
     *
     * 请求示例：
     *   GET /api/admin/analysis-feedback?analysisType=SENTIMENT&feedbackType=INACCURATE&merchantId=1&pageNum=1&pageSize=20
     *
     * @param analysisType 可选，按分析类型筛选
     * @param feedbackType 可选，按反馈类型筛选
     * @param merchantId   可选，按商家筛选
     * @param pageNum      页码，默认 1
     * @param pageSize     每页条数，默认 20
     * @return 分页反馈列表（含商家名称和用户名称）
     */
    @GetMapping("/api/admin/analysis-feedback")
    public ApiResponse<PageResult<AnalysisFeedbackVO>> listAllFeedback(
            @RequestParam(required = false) String analysisType,
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest servletRequest
    ) {
        requireAdminOrOperator(servletRequest);

        PageResult<AnalysisFeedbackVO> result = feedbackService.getAllFeedback(
                analysisType, feedbackType, merchantId, pageNum, pageSize);
        return ApiResponse.success(result);
    }

    // ============================================
    // 权限校验辅助方法
    // ============================================

    /**
     * 从请求属性中提取当前登录用户的 ID。
     *
     * userId 由 JwtInterceptor 在认证通过后设置到 request 属性中。
     */
    private Long requireUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    }

    /**
     * 验证当前用户是否属于指定商家（AC-5）。
     *
     * 查询 merchant_members 表，确认用户是该商家的成员。
     */
    private void verifyMerchantMembership(Long userId, Long merchantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT 1 FROM merchant_members " +
                        "WHERE user_id = ? AND merchant_id = ? AND status = 'ACTIVE' " +
                        "LIMIT 1",
                userId, merchantId
        );
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "您不是该商家的成员，无权操作");
        }
    }

    /**
     * 验证当前用户具有管理员或运营人员角色。
     */
    private void requireAdminOrOperator(HttpServletRequest request) {
        Object role = request.getAttribute("role");
        if (role instanceof String roleStr) {
            if ("ADMIN".equals(roleStr) || "OPERATOR".equals(roleStr)) {
                return;
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "需要管理员或运营人员权限");
    }
}
