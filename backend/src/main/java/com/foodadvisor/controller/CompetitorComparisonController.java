package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.competitor.CompetitorComparisonRequest;
import com.foodadvisor.dto.competitor.CompetitorComparisonResponse;
import com.foodadvisor.dto.competitor.CompetitorMerchantVO;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.CompetitorComparisonService;
import com.foodadvisor.service.AiRequestTraceService;
import com.foodadvisor.trace.AiTraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 周边竞品对比接口（EPIC-02 Story 6）
 *
 * 供商家端使用，提供以下能力：
 * 1. 查询可选的候选竞品列表（同区域、同品类、正常营业）
 * 2. 发起竞品对比分析（统计数据 + AI 分析）
 *
 * 权限控制：
 * - 所有接口需要 JWT 认证（/api/merchant-console/** 路径受拦截器保护）
 * - 只有本店关联的商家用户才能发起对比（验收准则 AC-7）
 *
 * 验收准则对齐：
 * - AC-1: 候选列表限制同区域、同/相似品类，最多选 3 家
 * - AC-2: 对比结果至少包含价格、评分、好评率、评价数量
 * - AC-3: AI 分析突出优势/短板，无明显差异时明确说明
 * - AC-5: 不符合限制的商家不可选，强制请求时拒绝
 * - AC-6: 前端可据此渲染图表和文字
 * - AC-7: 只能发起与自己店铺相关的对比
 */
@RestController
@RequestMapping("/api/merchant-console/merchants/{merchantId}/competitor-comparison")
public class CompetitorComparisonController {

    private static final Logger log =
            LoggerFactory.getLogger(CompetitorComparisonController.class);

    private final CompetitorComparisonService comparisonService;
    private final JdbcTemplate jdbcTemplate;
    private final AiRequestTraceService traceService;

    public CompetitorComparisonController(
            CompetitorComparisonService comparisonService,
            JdbcTemplate jdbcTemplate
    ) {
        this(comparisonService, jdbcTemplate, null);
    }

    @Autowired
    public CompetitorComparisonController(
            CompetitorComparisonService comparisonService,
            JdbcTemplate jdbcTemplate,
            AiRequestTraceService traceService
    ) {
        this.comparisonService = comparisonService;
        this.jdbcTemplate = jdbcTemplate;
        this.traceService = traceService;
    }

    /**
     * 获取候选竞品列表。
     *
     * 返回与指定商家（merchantId）同区域、同品类/菜系、
     * 正常营业的活跃商家列表，按综合评分降序排列。
     *
     * 前端据此渲染竞品选择器，用户可从中选择 1~3 家竞品进行对比。
     *
     * 请求示例：
     * GET /api/merchant-console/merchants/1/competitor-comparison/candidates
     *
     * @param merchantId     本店 ID（路径参数）
     * @param servletRequest HTTP 请求（用于获取当前用户身份）
     * @return 候选竞品列表（基础信息：名称、类别、评分、人均等）
     */
    @GetMapping("/candidates")
    public ApiResponse<List<CompetitorMerchantVO>> getCandidates(
            @PathVariable Long merchantId,
            HttpServletRequest servletRequest
    ) {
        // 验证当前用户属于该商家（验收准则 AC-7）
        Long userId = requireUserId(servletRequest);
        verifyMerchantMembership(userId, merchantId);

        log.info("查询候选竞品列表 merchantId={}", merchantId);
        List<CompetitorMerchantVO> candidates =
                comparisonService.getCandidateCompetitors(merchantId);
        return ApiResponse.success(candidates);
    }

    /**
     * 执行竞品对比分析。
     *
     * 流程：
     * 1. 验证用户属于本店
     * 2. 校验竞品合法性（区域+品类限制）
     * 3. 查询各商家详细统计数据（评分、好评率、差评问题等）
     * 4. 调用 AI 服务生成对比文字分析
     * 5. 返回完整对比结果（数据 + AI 分析）
     *
     * 请求示例：
     * POST /api/merchant-console/merchants/1/competitor-comparison/compare
     * Body: { "competitorMerchantIds": [2, 3, 4] }
     *
     * @param merchantId     本店 ID（路径参数）
     * @param request        竞品 ID 列表（1~3 个）
     * @param servletRequest HTTP 请求
     * @param servletResponse HTTP 响应（用于设置 X-Trace-Id header）
     * @return 完整对比结果
     */
    @PostMapping("/compare")
    public ApiResponse<CompetitorComparisonResponse> compare(
            @PathVariable Long merchantId,
            @Valid @RequestBody CompetitorComparisonRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        // 验证当前用户属于该商家（验收准则 AC-7）
        Long userId = requireUserId(servletRequest);
        verifyMerchantMembership(userId, merchantId);

        log.info("发起竞品对比 merchantId={}, competitorIds={}",
                merchantId, request.getCompetitorMerchantIds());

        // 带追踪上下文执行对比
        AiTraceContext context = traceService == null
                ? AiTraceContext.create(null, userId, null, "COMPETITOR_ANALYSIS")
                : traceService.startTrace(null, null, userId, "COMPETITOR_ANALYSIS");

        CompetitorComparisonResponse result;
        try {
            result = traceService == null
                    ? comparisonService.performComparison(merchantId, request)
                    : comparisonService.performComparison(merchantId, request, context);

            if (context != null && result.getBusinessTraceId() == null) {
                result.setBusinessTraceId(context.traceId());
            }

            if (context != null) {
                servletResponse.setHeader("X-Trace-Id", context.traceId());
            }
        } catch (RuntimeException exception) {
            if (context != null) {
                traceService.failTraceSafely(context,
                        "COMPETITOR_ANALYSIS_FAILED", exception.getMessage());
            }
            if (context != null) {
                servletResponse.setHeader("X-Trace-Id", context.traceId());
            }
            throw exception;
        }

        // 根据状态返回不同消息
        if ("FAILED".equals(result.getComparisonStatus())) {
            return ApiResponse.success("对比数据已生成，但 AI 分析失败", result);
        }

        return ApiResponse.success("竞品对比分析完成", result);
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
     * 验证当前用户是否属于指定商家。
     *
     * 查询 merchant_members 表，确认用户是该商家的成员（OWNER/MANAGER/STAFF）。
     * 如果不属于，抛出 403。
     *
     * 验收准则 AC-7：商家用户只能发起与自己店铺相关的竞品对比。
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
                    "您不是该商家的成员，无法发起竞品对比");
        }
    }
}
