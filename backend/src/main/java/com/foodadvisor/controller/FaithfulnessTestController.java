package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.security.TraceAccessGuard;
import com.foodadvisor.service.AIClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 评价摘要忠实性测试接口（EPIC-06 Story 3）
 *
 * 提供前端执行摘要忠实性测试、查看历史、管理优化清单等功能。
 * 核心测试逻辑委托给 AI 服务（FastAPI）的 LLM-as-Judge 完成。
 */
@RestController
@RequestMapping("/api/admin/review-summary/faithfulness-test")
public class FaithfulnessTestController {

    private static final Logger log =
            LoggerFactory.getLogger(FaithfulnessTestController.class);

    private final AIClientService aiClientService;
    private final TraceAccessGuard accessGuard;
    private final ObjectMapper objectMapper;

    /** 内存存储测试记录（后续可迁移到数据库） */
    private final Map<Long, Map<String, Object>> testRecords = new LinkedHashMap<>();
    private final Map<Long, Map<String, Object>> optimizationItems = new LinkedHashMap<>();
    private long nextTestId = 1;
    private long nextItemId = 1;

    public FaithfulnessTestController(
            AIClientService aiClientService,
            TraceAccessGuard accessGuard,
            ObjectMapper objectMapper
    ) {
        this.aiClientService = aiClientService;
        this.accessGuard = accessGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行评价摘要忠实性测试
     *
     * POST /api/admin/review-summary/faithfulness-test/run
     * Body: { merchantId, summary, reviews }
     */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> runTest(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        if (!body.containsKey("merchantId") || !body.containsKey("summary")) {
            return ApiResponse.failure("INVALID_PARAMS", "缺少必填参数: merchantId, summary");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reviews =
                (List<Map<String, Object>>) body.getOrDefault("reviews", Collections.emptyList());

        if (reviews.isEmpty()) {
            return ApiResponse.failure("INVALID_PARAMS", "评价列表不能为空");
        }

        try {
            Map<String, Object> aiRequest = new LinkedHashMap<>();
            aiRequest.put("requestId", "faithfulness-" + body.get("merchantId") + "-" + System.currentTimeMillis());
            aiRequest.put("merchantId", body.get("merchantId"));
            aiRequest.put("summary", body.get("summary"));
            aiRequest.put("reviews", reviews);

            log.info("执行摘要忠实性测试 merchantId={}, reviewCount={}",
                    body.get("merchantId"), reviews.size());

            JsonNode aiResult = aiClientService.testSummaryFaithfulness(aiRequest);

            Map<String, Object> result = objectMapper.treeToValue(aiResult, Map.class);

            // 保存测试记录
            long testId = nextTestId++;
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", testId);
            record.put("merchantId", body.get("merchantId"));
            record.put("testStatus", result.getOrDefault("testStatus", "SUCCESS"));
            record.put("overallScore", result.getOrDefault("overallScore", 0.0));
            record.put("totalClaims", result.getOrDefault("totalClaims", 0));
            record.put("faithfulCount", result.getOrDefault("faithfulCount", 0));
            record.put("unfaithfulCount", result.getOrDefault("unfaithfulCount", 0));
            record.put("uncertainCount", result.getOrDefault("uncertainCount", 0));
            record.put("modelName", result.getOrDefault("modelName", null));
            record.put("modelVersion", result.getOrDefault("modelVersion", null));
            record.put("promptVersion", result.getOrDefault("promptVersion", "faithfulness-test:v1"));
            record.put("createdAt", new Date());
            record.put("claimResults", result.getOrDefault("claimResults", Collections.emptyList()));
            record.put("summaryText", result.getOrDefault("summaryText", null));
            testRecords.put(testId, record);

            // 返回结果包含 testId
            Map<String, Object> response = new LinkedHashMap<>(result);
            response.put("id", testId);
            response.put("createdAt", record.get("createdAt"));

            return ApiResponse.success("忠实性测试完成", response);

        } catch (Exception e) {
            log.error("忠实性测试执行失败 merchantId={}: {}", body.get("merchantId"), e.getMessage(), e);
            return ApiResponse.failure("TEST_FAILED", "忠实性测试执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取商家测试历史
     *
     * GET /api/admin/review-summary/faithfulness-test/history/{merchantId}
     */
    @GetMapping("/history/{merchantId}")
    public ApiResponse<List<Map<String, Object>>> getTestHistory(
            @PathVariable Long merchantId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        List<Map<String, Object>> history = new ArrayList<>();
        for (Map<String, Object> record : testRecords.values()) {
            Object mid = record.get("merchantId");
            if (mid != null && Long.valueOf(mid.toString()).equals(merchantId)) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", record.get("id"));
                summary.put("merchantId", record.get("merchantId"));
                summary.put("testStatus", record.get("testStatus"));
                summary.put("overallScore", record.get("overallScore"));
                summary.put("totalClaims", record.get("totalClaims"));
                summary.put("faithfulCount", record.get("faithfulCount"));
                summary.put("unfaithfulCount", record.get("unfaithfulCount"));
                summary.put("uncertainCount", record.get("uncertainCount"));
                summary.put("modelName", record.get("modelName"));
                summary.put("modelVersion", record.get("modelVersion"));
                summary.put("createdAt", record.get("createdAt"));
                history.add(summary);
            }
        }

        // 按时间倒序
        history.sort((a, b) -> {
            Date da = (Date) a.getOrDefault("createdAt", new Date(0));
            Date db = (Date) b.getOrDefault("createdAt", new Date(0));
            return db.compareTo(da);
        });

        return ApiResponse.success(history);
    }

    /**
     * 获取单次测试详情
     *
     * GET /api/admin/review-summary/faithfulness-test/{testId}
     */
    @GetMapping("/{testId}")
    public ApiResponse<Map<String, Object>> getTestDetail(
            @PathVariable Long testId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        Map<String, Object> record = testRecords.get(testId);
        if (record == null) {
            return ApiResponse.notFound("测试记录不存在: " + testId);
        }

        return ApiResponse.success(record);
    }

    /**
     * 加入优化清单
     *
     * POST /api/admin/review-summary/faithfulness-test/{testId}/optimization-items
     * Body: { merchantId, items: [{ claimIndex, claimType, claimText, verdict, issueType, reasoning }] }
     */
    @PostMapping("/{testId}/optimization-items")
    public ApiResponse<List<Map<String, Object>>> addOptimizationItems(
            @PathVariable Long testId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) body.getOrDefault("items", Collections.emptyList());

        if (items.isEmpty()) {
            return ApiResponse.failure("INVALID_PARAMS", "优化清单项不能为空");
        }

        Long merchantId = body.containsKey("merchantId")
                ? Long.valueOf(body.get("merchantId").toString()) : null;

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> item : items) {
            long itemId = nextItemId++;
            Map<String, Object> optItem = new LinkedHashMap<>();
            optItem.put("id", itemId);
            optItem.put("testId", testId);
            optItem.put("merchantId", merchantId);
            optItem.put("claimIndex", item.get("claimIndex"));
            optItem.put("claimType", item.getOrDefault("claimType", ""));
            optItem.put("claimText", item.getOrDefault("claimText", ""));
            optItem.put("verdict", item.getOrDefault("verdict", "UNFAITHFUL"));
            optItem.put("issueType", item.getOrDefault("issueType", "OTHER"));
            optItem.put("reasoning", item.getOrDefault("reasoning", ""));
            optItem.put("status", "OPEN");
            optItem.put("createdAt", new Date());
            optItem.put("updatedAt", new Date());
            optimizationItems.put(itemId, optItem);
            created.add(new LinkedHashMap<>(optItem));
        }

        log.info("添加优化清单项 testId={}, count={}", testId, created.size());
        return ApiResponse.success("已添加到优化清单", created);
    }

    /**
     * 查询优化清单列表
     *
     * GET /api/admin/review-summary/faithfulness-test/optimization-items
     */
    @GetMapping("/optimization-items")
    public ApiResponse<List<Map<String, Object>>> listOptimizationItems(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : optimizationItems.values()) {
            boolean match = true;
            if (merchantId != null) {
                Object mid = item.get("merchantId");
                match = mid != null && Long.valueOf(mid.toString()).equals(merchantId);
            }
            if (match && status != null && !status.isEmpty()) {
                match = status.equals(item.get("status"));
            }
            if (match) {
                // 补充商家名称（简化处理）
                Map<String, Object> enriched = new LinkedHashMap<>(item);
                enriched.putIfAbsent("merchantName", "商家 #" + item.get("merchantId"));
                result.add(enriched);
            }
        }

        result.sort((a, b) -> {
            Date da = (Date) a.getOrDefault("createdAt", new Date(0));
            Date db = (Date) b.getOrDefault("createdAt", new Date(0));
            return db.compareTo(da);
        });

        return ApiResponse.success(result);
    }

    /**
     * 更新优化清单项状态
     *
     * PUT /api/admin/review-summary/faithfulness-test/optimization-items/{itemId}
     * Body: { status, resolution }
     */
    @PutMapping("/optimization-items/{itemId}")
    public ApiResponse<Map<String, Object>> updateOptimizationItem(
            @PathVariable Long itemId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        Map<String, Object> item = optimizationItems.get(itemId);
        if (item == null) {
            return ApiResponse.notFound("优化清单项不存在: " + itemId);
        }

        if (body.containsKey("status")) {
            item.put("status", body.get("status"));
        }
        if (body.containsKey("resolution")) {
            item.put("resolution", body.get("resolution"));
        }
        item.put("updatedAt", new Date());

        log.info("优化清单项状态更新 itemId={}, newStatus={}", itemId, item.get("status"));

        return ApiResponse.success("状态更新成功", new LinkedHashMap<>(item));
    }

    /**
     * 对比两次测试结果
     *
     * GET /api/admin/review-summary/faithfulness-test/compare
     */
    @GetMapping("/compare")
    public ApiResponse<Map<String, Object>> compareTests(
            @RequestParam Long baselineTestId,
            @RequestParam Long candidateTestId,
            HttpServletRequest request
    ) {
        accessGuard.requireOperatorOrAdmin(request);

        Map<String, Object> baseline = testRecords.get(baselineTestId);
        Map<String, Object> candidate = testRecords.get(candidateTestId);

        if (baseline == null || candidate == null) {
            return ApiResponse.failure("NOT_FOUND", "测试记录不存在");
        }

        double baselineScore = toDouble(baseline.get("overallScore"));
        double candidateScore = toDouble(candidate.get("overallScore"));
        int baselineUnfaithful = toInt(baseline.get("unfaithfulCount"));
        int candidateUnfaithful = toInt(candidate.get("unfaithfulCount"));

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("baselineTestId", baselineTestId);
        comparison.put("candidateTestId", candidateTestId);
        comparison.put("baselineScore", baselineScore);
        comparison.put("candidateScore", candidateScore);
        comparison.put("scoreChange", candidateScore - baselineScore);
        comparison.put("baselineUnfaithfulCount", baselineUnfaithful);
        comparison.put("candidateUnfaithfulCount", candidateUnfaithful);
        comparison.put("unfaithfulCountChange", candidateUnfaithful - baselineUnfaithful);

        // 简化：基于不忠实声明数量变化判断改进/退步
        int improvedCount = Math.max(0, baselineUnfaithful - candidateUnfaithful);
        int regressedCount = Math.max(0, candidateUnfaithful - baselineUnfaithful);
        comparison.put("improvedCount", improvedCount);
        comparison.put("regressedCount", regressedCount);
        comparison.put("unchangedCount",
                Math.max(0, Math.min(
                        toInt(baseline.get("totalClaims")),
                        toInt(candidate.get("totalClaims"))
                ) - improvedCount - regressedCount));

        if (improvedCount > 0 && regressedCount == 0) {
            comparison.put("comparisonNote", "候选测试整体优于基准测试，不忠实声明数量减少。");
        } else if (regressedCount > 0 && improvedCount == 0) {
            comparison.put("comparisonNote", "候选测试存在退步，新增不忠实声明。需要检查摘要生成或评判模型变更。");
        } else if (regressedCount > 0) {
            comparison.put("comparisonNote", "两次测试各有优劣，建议逐一对比声明级判定变化。");
        } else {
            comparison.put("comparisonNote", "两次测试结果无明显变化。");
        }

        return ApiResponse.success(comparison);
    }

    // -- helper methods --

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return 0; }
    }
}
