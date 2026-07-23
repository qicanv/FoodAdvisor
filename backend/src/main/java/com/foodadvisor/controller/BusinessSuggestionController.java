package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.suggestion.BusinessSuggestionEvidenceVO;
import com.foodadvisor.dto.suggestion.BusinessSuggestionVO;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.service.AiRequestTraceService;
import com.foodadvisor.service.BusinessSuggestionService;
import com.foodadvisor.trace.AiTraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 经营改进建议生成接口（EPIC-02 Story 8）
 *
 * 提供商家端建议展示和生成/刷新能力。
 *
 * 权限控制：
 * - 所有 /api/merchant-console/** 接口需要 JWT 认证
 * - 只有本店关联的商家用户才能查看和生成建议（验收准则：商家只能管理自己店铺）
 *
 * 验收准则对齐：
 * - AC-1: 每项建议至少关联一个口碑趋势、差评类别、商家亮点或竞品对比数据
 * - AC-2: 每项建议展示对应指标、数量、占比或原评论依据
 * - AC-3: 每项建议至少包含问题对象、改进措施和适用时间范围
 * - AC-4: 建议标记为短期或长期
 * - AC-5: 数据量低于配置阈值时显示依据有限，降低结论确定性
 * - AC-7: 点击建议依据能够查看对应统计或原始评论
 */
@RestController
public class BusinessSuggestionController {

    private static final Logger log =
            LoggerFactory.getLogger(BusinessSuggestionController.class);

    private final BusinessSuggestionService suggestionService;
    private final JdbcTemplate jdbcTemplate;
    private final AiRequestTraceService traceService;

    public BusinessSuggestionController(
            BusinessSuggestionService suggestionService,
            JdbcTemplate jdbcTemplate
    ) {
        this(suggestionService, jdbcTemplate, null);
    }

    @Autowired
    public BusinessSuggestionController(
            BusinessSuggestionService suggestionService,
            JdbcTemplate jdbcTemplate,
            AiRequestTraceService traceService
    ) {
        this.suggestionService = suggestionService;
        this.jdbcTemplate = jdbcTemplate;
        this.traceService = traceService;
    }

    // ==================== 用户端（只读） ====================

    /**
     * 用户端：获取商家当前经营改进建议列表。
     *
     * 只读缓存结果，不触发模型调用。
     *
     * 状态说明：
     * - 正常返回建议列表（status=ACTIVE）
     * - 从未生成过（generationStatus=NONE）
     * - 数据不足（generationStatus=INSUFFICIENT_DATA）
     *
     * 请求示例：GET /api/merchants/1/business-suggestions
     */
    @GetMapping("/api/merchants/{merchantId}/business-suggestions")
    public ApiResponse<List<BusinessSuggestionVO>> getSuggestions(
            @PathVariable Long merchantId
    ) {
        return ApiResponse.success(
                suggestionService.getDisplaySuggestions(merchantId));
    }

    /**
     * 用户端：查看建议依据（溯源到统计数据或原始评价）。
     *
     * 每项建议都可溯源到对应的数据来源（验收准则 AC-7）。
     *
     * 请求示例：
     *   GET /api/merchants/1/business-suggestions/evidences?suggestionId=5
     *
     * @param suggestionId 可选，不传则返回该商家所有活跃建议的依据
     */
    @GetMapping("/api/merchants/{merchantId}/business-suggestions/evidences")
    public ApiResponse<List<BusinessSuggestionEvidenceVO>> getEvidences(
            @PathVariable Long merchantId,
            @RequestParam(required = false) Long suggestionId
    ) {
        return ApiResponse.success(
                suggestionService.getEvidences(merchantId, suggestionId));
    }

    // ==================== 商家端（生成/刷新） ====================

    /**
     * 商家端：生成或刷新经营改进建议。
     *
     * 系统结合近期情感趋势、差评归因、商家亮点和竞品对比结果，
     * 调用 AI 服务为商家生成阶段性经营改进建议。
     *
     * 未达刷新条件（缓存未过期）时直接返回已有建议，
     * force=true 可强制重新生成。
     *
     * 触发时机建议：
     * - 商家用户手动点击"生成经营建议"按钮
     * - 新增评价或口碑数据显著变化后系统提示可刷新
     *
     * 请求示例：
     *   POST /api/merchant-console/merchants/1/business-suggestions/generate
     *   POST /api/merchant-console/merchants/1/business-suggestions/generate?force=true
     */
    @PostMapping("/api/merchant-console/merchants/{merchantId}/business-suggestions/generate")
    public ApiResponse<List<BusinessSuggestionVO>> generate(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "false") boolean force,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        // 验证当前用户属于该商家
        Long userId = requireUserId(servletRequest);
        verifyMerchantMembership(userId, merchantId);

        log.info("生成经营改进建议 merchantId={}, force={}", merchantId, force);

        AiTraceContext context = traceService == null ? null
                : traceService.startTrace(null, null, null,
                "BUSINESS_SUGGESTION_GENERATION");

        List<BusinessSuggestionVO> suggestions;
        try {
            suggestions = traceService == null
                    ? suggestionService.generateSuggestions(merchantId, force)
                    : suggestionService.generateSuggestions(merchantId, force, context);

            if (context != null) {
                servletResponse.setHeader("X-Trace-Id", context.traceId());
            }
        } catch (RuntimeException exception) {
            if (context != null) {
                traceService.failTraceSafely(context,
                        "SUGGESTION_GENERATION_FAILED", exception.getMessage());
                servletResponse.setHeader("X-Trace-Id", context.traceId());
            }
            throw exception;
        }

        // 根据状态返回不同消息
        if (suggestions.size() == 1) {
            String genStatus = suggestions.get(0).getGenerationStatus();
            if ("INSUFFICIENT_DATA".equals(genStatus)) {
                return ApiResponse.success(
                        "数据量不足，无法生成可靠的经营改进建议",
                        suggestions);
            }
            if ("NONE".equals(genStatus)) {
                return ApiResponse.success(
                        "未生成经营改进建议",
                        suggestions);
            }
        }

        return ApiResponse.success(
                "经营改进建议生成完成",
                suggestions);
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
                    "您不是该商家的成员，无法生成经营改进建议");
        }
    }
}
