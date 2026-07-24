package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.entity.ContentRiskRecord;
import com.foodadvisor.security.AdminAccessGuard;
import com.foodadvisor.service.ViolationTextService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 违规文本识别管理接口
 *
 * <p>提供违规检测记录的查询和统计，供管理后台违规文本分析页使用。</p>
 *
 * <p>与 {@link ModerationController}（内容审核工作台）和
 * {@link FraudDetectionController}（刷评检测）共同构成
 * 平台内容安全体系的三根支柱。</p>
 *
 * <h3>三者差异</h3>
 * <ul>
 *   <li><b>违规文本识别</b>：单条内容的文字质量分析（广告/谩骂/虚假/灌水）</li>
 *   <li><b>刷评检测</b>：多条评价的行为模式分析（集中度/相似度/频率/评分异常）</li>
 *   <li><b>内容审核工作台</b>：统一的人工审核操作界面</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/violation-text")
public class ViolationTextController {

    private final ViolationTextService violationTextService;
    private final AdminAccessGuard adminAccessGuard;

    public ViolationTextController(
            ViolationTextService violationTextService,
            AdminAccessGuard adminAccessGuard
    ) {
        this.violationTextService = violationTextService;
        this.adminAccessGuard = adminAccessGuard;
    }

    /**
     * 查询指定内容的违规检测记录。
     *
     * @param contentType 内容类型（REVIEW / REVIEW_FOLLOW_UP）
     * @param contentId   内容ID（reviewId）
     */
    @GetMapping("/risk-records")
    public ApiResponse<List<Map<String, Object>>> getRiskRecords(
            @RequestParam String contentType,
            @RequestParam Long contentId,
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        List<ContentRiskRecord> records = violationTextService.getRecords(
                contentType, contentId);

        List<Map<String, Object>> result = records.stream()
                .map(this::toMap)
                .collect(Collectors.toList());

        return ApiResponse.success(result);
    }

    /**
     * 违规检测统计概览。
     *
     * <p>返回各维度的统计数据，用于违规文本分析仪表盘。</p>
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(
            HttpServletRequest request
    ) {
        adminAccessGuard.requireAdmin(request);

        Map<String, Object> stats = new LinkedHashMap<>();

        // 按风险类型统计
        List<Map<String, Object>> byRiskType = buildRiskTypeStats();
        stats.put("byRiskType", byRiskType);

        // 按风险等级统计
        List<Map<String, Object>> byRiskLevel = buildRiskLevelStats();
        stats.put("byRiskLevel", byRiskLevel);

        // 按检测状态统计
        List<Map<String, Object>> byDetectionStatus = buildDetectionStatusStats();
        stats.put("byDetectionStatus", byDetectionStatus);

        // 汇总
        long totalDetections = byRiskType.stream()
                .mapToLong(m -> ((Number) m.getOrDefault("count", 0)).longValue())
                .sum();
        stats.put("totalDetections", totalDetections);

        return ApiResponse.success(stats);
    }

    // ==================== 统计辅助方法 ====================

    private List<Map<String, Object>> buildRiskTypeStats() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] types = {"AD_SPAM", "ABUSE", "FALSE_AD", "SPAM", "OTHER"};
        String[] names = {"广告引流", "恶意谩骂", "虚假宣传", "无关灌水", "其他违规"};

        // 全量统计（不限时间范围）
        List<Map<String, Object>> dbStats =
                violationTextService.getRiskTypeStatsAll();

        for (int i = 0; i < types.length; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("riskType", types[i]);
            item.put("name", names[i]);
            item.put("count", findCount(dbStats, types[i]));
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> buildRiskLevelStats() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] levels = {"HIGH", "MEDIUM", "LOW"};

        // 全量统计（不限时间范围）
        List<Map<String, Object>> dbStats =
                violationTextService.getRiskLevelStatsAll();

        for (String level : levels) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("riskLevel", level);
            item.put("count", findCount(dbStats, level));
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> buildDetectionStatusStats() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] statuses = {"SUCCESS", "FALLBACK", "ERROR", "TIMEOUT"};
        String[] names = {"AI 检测成功", "降级关键词", "检测失败", "超时"};

        // 全量统计（不限时间范围）
        List<Map<String, Object>> dbStats =
                violationTextService.getDetectionStatusStatsAll();

        for (int i = 0; i < statuses.length; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", statuses[i]);
            item.put("name", names[i]);
            item.put("count", findCount(dbStats, statuses[i]));
            list.add(item);
        }
        return list;
    }

    private long findCount(List<Map<String, Object>> stats, String key) {
        return stats.stream()
                .filter(m -> key.equals(m.get("key")))
                .findFirst()
                .map(m -> ((Number) m.getOrDefault("cnt", 0)).longValue())
                .orElse(0L);
    }

    /**
     * 将 ContentRiskRecord 转为前端友好的 Map。
     */
    private Map<String, Object> toMap(ContentRiskRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("contentType", record.getContentType());
        map.put("contentId", record.getContentId());
        map.put("contentVersion", record.getContentVersion());
        map.put("ruleVersion", record.getRuleVersion());
        map.put("riskType", record.getRiskType());
        map.put("riskLevel", record.getRiskLevel());
        map.put("riskScore", record.getRiskScore());
        map.put("matchedRules", record.getMatchedRules());
        map.put("maskedExcerpt", record.getMaskedExcerpt());
        map.put("detectionStatus", record.getDetectionStatus());
        map.put("modelName", record.getModelName());
        map.put("businessTraceId", record.getBusinessTraceId());
        map.put("createdAt", record.getCreatedAt());
        return map;
    }
}
