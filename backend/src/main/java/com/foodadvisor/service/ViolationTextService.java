package com.foodadvisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.violation.ViolationTextResult;
import com.foodadvisor.entity.ContentRiskRecord;
import com.foodadvisor.mapper.ContentRiskRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 违规文本检测服务
 *
 * <p>实现 EPIC-03 故事3「违规文本识别」的核心检测逻辑。
 * 调用 AI 服务进行智能违规检测，检测失败时降级到关键词匹配。</p>
 *
 * <h3>检测流程</h3>
 * <ol>
 *   <li>调用 AIClientService.checkViolationText() → AI 服务 LLM 检测</li>
 *   <li>成功：解析 LLM 返回的风险类型、等级、分值和命中规则</li>
 *   <li>失败/超时：降级到关键词匹配（广告/谩骂/灌水/严重违规四类词库）</li>
 *   <li>保存检测记录到 content_risk_records 表</li>
 *   <li>返回 ViolationTextResult 供 ReviewService 判定</li>
 * </ol>
 *
 * <h3>风险等级判定</h3>
 * <ul>
 *   <li>HIGH（score >= 70）：明显违规，阻止发布</li>
 *   <li>MEDIUM（score 40~69）：疑似违规，进入人工审核</li>
 *   <li>LOW（score < 40）：基本正常，自动通过</li>
 * </ul>
 *
 * @see AIClientService#checkViolationText(String, String)
 * @see ReviewService#applyContentSafety
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ViolationTextService {

    /** 高风险关键词（降级时使用 — 暴恐/涉政/色情/赌博/毒品） */
    private static final Set<String> SEVERE_RISK_WORDS = Set.of(
            "暴恐", "涉政", "色情", "赌博", "毒品"
    );

    /** 广告引流关键词（降级时使用） */
    private static final Set<String> AD_SPAM_KEYWORDS = Set.of(
            "加微信", "加QQ", "加群", "扫码", "微信号", "手机号",
            "加我", "私聊", "私信", "加好友", "免费领取", "点击链接",
            "刷单", "代购", "代刷", "刷好评", "刷评", "返现", "返利",
            "加V", "V信", "企鹅号"
    );

    /** 恶意谩骂关键词（降级时使用） */
    private static final Set<String> ABUSE_KEYWORDS = Set.of(
            "傻逼", "sb", "SB", "傻X", "傻x", "妈的", "你妈", "操你",
            "垃圾店", "去死", "滚蛋", "畜生", "废物", "狗屎", "王八",
            "日你", "尼玛", "草泥马", "他妈的", "狗屁", "贱"
    );

    /** 无关灌水模式（降级时使用）— 同一字符重复5次以上 */
    private static final java.util.regex.Pattern SPAM_REPEAT_PATTERN =
            java.util.regex.Pattern.compile("(.)\\1{4,}");

    /** 风险分值阈值：>= HIGH_THRESHOLD 为高风险 */
    private static final int HIGH_THRESHOLD = 70;

    /** 风险分值阈值：>= MEDIUM_THRESHOLD 为中风险 */
    private static final int MEDIUM_THRESHOLD = 40;

    private final AIClientService aiClientService;
    private final ContentRiskRecordMapper riskRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 对文本内容执行违规检测（不保存记录到数据库）。
     *
     * <p>用于评价保存前的预检测，此时 review.id 可能尚未生成。
     * 检测记录需在评价保存后通过 {@link #saveRecord} 单独写入。</p>
     *
     * @param content     待检测的文本内容
     * @param ruleVersion 检测规则版本
     * @return ViolationTextResult 包含风险等级、分值和命中规则
     */
    public ViolationTextResult detectViolation(String content, String ruleVersion) {
        if (content == null || content.isBlank()) {
            return ViolationTextResult.error("content is blank");
        }

        try {
            // Step 1: 调用 AI 服务进行违规检测
            JsonNode aiResponse = aiClientService.checkViolationText(content, ruleVersion);
            return parseAiResponse(aiResponse);

        } catch (Exception e) {
            log.warn("AI 违规检测失败，降级到关键词匹配: error={}", e.getMessage());

            // Step 2: 降级到关键词匹配
            return fallbackKeywordCheck(content);
        }
    }

    /**
     * 将违规检测结果保存到 content_risk_records 表。
     *
     * @param content        原始文本内容
     * @param contentType    内容类型（REVIEW / REVIEW_FOLLOW_UP）
     * @param contentId      内容ID（reviewId，必须在评价保存后调用）
     * @param contentVersion 内容版本号
     * @param ruleVersion    规则版本
     * @param result         检测结果
     */
    @Transactional
    public void saveRecord(
            String content,
            String contentType,
            Long contentId,
            Integer contentVersion,
            String ruleVersion,
            ViolationTextResult result
    ) {
        saveRiskRecord(content, contentType, contentId, contentVersion,
                ruleVersion, result);
    }

    /**
     * 对文本内容执行违规检测（保存记录到数据库）。
     *
     * @deprecated 推荐使用 {@link #detectViolation(String, String)} + {@link #saveRecord}
     *             分两步调用，以兼容评价保存前 ID 未生成的场景。
     *
     * @param content        待检测的文本内容
     * @param contentType    内容类型（REVIEW / REVIEW_FOLLOW_UP / CHAT_MESSAGE）
     * @param contentId      内容ID（reviewId / messageId）
     * @param contentVersion 内容版本号
     * @return ViolationTextResult 包含风险等级、分值和命中规则
     */
    public ViolationTextResult checkViolation(
            String content,
            String contentType,
            Long contentId,
            Integer contentVersion
    ) {
        return checkViolation(content, contentType, contentId, contentVersion, "violation-detection:v1");
    }

    /**
     * @deprecated 推荐使用 {@link #detectViolation(String, String)} + {@link #saveRecord}
     */
    public ViolationTextResult checkViolation(
            String content,
            String contentType,
            Long contentId,
            Integer contentVersion,
            String ruleVersion
    ) {
        if (content == null || content.isBlank()) {
            return ViolationTextResult.error("content is blank");
        }

        ViolationTextResult result;

        try {
            JsonNode aiResponse = aiClientService.checkViolationText(content, ruleVersion);
            result = parseAiResponse(aiResponse);
        } catch (Exception e) {
            log.warn("AI 违规检测失败，降级到关键词匹配: contentId={}, error={}",
                    contentId, e.getMessage());
            result = fallbackKeywordCheck(content);
        }

        saveRiskRecord(content, contentType, contentId, contentVersion,
                ruleVersion, result);

        return result;
    }

    /**
     * 解析 AI 服务返回的检测结果。
     */
    ViolationTextResult parseAiResponse(JsonNode response) {
        try {
            String detectionStatus = response.path("detectionStatus").asText("SUCCESS");
            String riskLevel = response.path("riskLevel").asText("LOW").toUpperCase();
            int riskScore = clampScore(response.path("riskScore").asInt(0));
            String riskType = response.path("riskType").asText();
            if (riskType == null || riskType.isBlank() || "null".equals(riskType)) {
                riskType = null;
            }
            String modelName = response.path("modelName").asText(null);
            String businessTraceId = response.path("businessTraceId").asText(null);
            String errorMessage = response.path("errorMessage").asText(null);

            // 如果 AI 检测本身失败（ERROR），降级
            if ("ERROR".equals(detectionStatus) || "TIMEOUT".equals(detectionStatus)) {
                log.warn("AI 检测返回异常状态: status={}, error={}", detectionStatus, errorMessage);
                return ViolationTextResult.error(errorMessage != null ? errorMessage : "AI detection failed");
            }

            // 解析命中规则
            List<ViolationTextResult.MatchedRuleInfo> matchedRules = parseMatchedRules(
                    response.path("matchedRules"));

            // 根据风险分值修正风险等级
            if (riskScore >= HIGH_THRESHOLD && !"HIGH".equals(riskLevel)) {
                riskLevel = "HIGH";
            } else if (riskScore >= MEDIUM_THRESHOLD && "LOW".equals(riskLevel)) {
                riskLevel = "MEDIUM";
            }

            boolean violation = !"LOW".equals(riskLevel);

            return ViolationTextResult.builder()
                    .violation(violation)
                    .riskLevel(riskLevel)
                    .riskScore(riskScore)
                    .riskType(riskType)
                    .matchedRules(matchedRules)
                    .detectionStatus(detectionStatus)
                    .modelName(modelName)
                    .businessTraceId(businessTraceId)
                    .errorMessage(errorMessage)
                    .build();

        } catch (Exception e) {
            log.error("解析 AI 检测响应失败: {}", e.getMessage(), e);
            return ViolationTextResult.error("Failed to parse AI response: " + e.getMessage());
        }
    }

    /**
     * 降级：使用关键词匹配检测违规。
     *
     * <p>在 AI 服务不可用时使用。覆盖广告引流、恶意谩骂、无关灌水三大类，
     * 严重违规词（暴恐/涉政等）直接标记为高风险。</p>
     */
    ViolationTextResult fallbackKeywordCheck(String content) {
        if (content == null || content.isBlank()) {
            return ViolationTextResult.fallback(false, "LOW", 0);
        }

        List<ViolationTextResult.MatchedRuleInfo> rules = new ArrayList<>();
        int maxScore = 0;
        String primaryRiskType = null;

        // 1. 检测严重违规（暴恐/涉政/色情/赌博/毒品）— 直接 HIGH
        for (String word : SEVERE_RISK_WORDS) {
            if (content.contains(word)) {
                rules.add(ViolationTextResult.MatchedRuleInfo.builder()
                        .ruleCode("SEVERE_001")
                        .ruleName("包含严重违规词语")
                        .riskType("OTHER")
                        .confidence(1.0)
                        .evidenceExcerpt(word)
                        .build());
                maxScore = Math.max(maxScore, 90);
                primaryRiskType = "OTHER";
            }
        }

        // 2. 检测广告引流
        for (String word : AD_SPAM_KEYWORDS) {
            if (content.contains(word)) {
                rules.add(ViolationTextResult.MatchedRuleInfo.builder()
                        .ruleCode("AD_SPAM_FB")
                        .ruleName("包含广告引流词语")
                        .riskType("AD_SPAM")
                        .confidence(0.85)
                        .evidenceExcerpt(word)
                        .build());
                maxScore = Math.max(maxScore, 75);
                primaryRiskType = "AD_SPAM";
            }
        }

        // 3. 检测恶意谩骂
        for (String word : ABUSE_KEYWORDS) {
            if (content.contains(word)) {
                rules.add(ViolationTextResult.MatchedRuleInfo.builder()
                        .ruleCode("ABUSE_FB")
                        .ruleName("包含恶意谩骂词语")
                        .riskType("ABUSE")
                        .confidence(0.85)
                        .evidenceExcerpt(word)
                        .build());
                maxScore = Math.max(maxScore, 72);
                primaryRiskType = "ABUSE";
            }
        }

        // 4. 检测无关灌水（重复字符）
        if (SPAM_REPEAT_PATTERN.matcher(content).find()) {
            rules.add(ViolationTextResult.MatchedRuleInfo.builder()
                    .ruleCode("SPAM_FB")
                    .ruleName("包含重复无意义字符")
                    .riskType("SPAM")
                    .confidence(0.75)
                    .evidenceExcerpt("重复字符模式")
                    .build());
            maxScore = Math.max(maxScore, 50);
            if (primaryRiskType == null) {
                primaryRiskType = "SPAM";
            }
        }

        // 5. 检测纯无意义内容（只有数字/符号/单字重复）
        String stripped = content.replaceAll("[\\s\\p{Punct}0-9]", "");
        if (stripped.length() < 3 && content.length() > 5) {
            rules.add(ViolationTextResult.MatchedRuleInfo.builder()
                    .ruleCode("SPAM_FB2")
                    .ruleName("疑似无意义灌水内容")
                    .riskType("SPAM")
                    .confidence(0.6)
                    .evidenceExcerpt(content.length() > 30
                            ? content.substring(0, 30) + "..." : content)
                    .build());
            maxScore = Math.max(maxScore, 45);
            if (primaryRiskType == null) {
                primaryRiskType = "SPAM";
            }
        }

        if (rules.isEmpty()) {
            return ViolationTextResult.fallback(false, "LOW", 10);
        }

        String riskLevel = maxScore >= HIGH_THRESHOLD ? "HIGH"
                : maxScore >= MEDIUM_THRESHOLD ? "MEDIUM" : "LOW";

        return ViolationTextResult.builder()
                .violation(true)
                .riskLevel(riskLevel)
                .riskScore(maxScore)
                .riskType(primaryRiskType)
                .matchedRules(rules)
                .detectionStatus("FALLBACK")
                .modelName("keyword-fallback")
                .build();
    }

    /**
     * 将检测结果保存到 content_risk_records 表。
     */
    @Transactional
    void saveRiskRecord(
            String content,
            String contentType,
            Long contentId,
            Integer contentVersion,
            String ruleVersion,
            ViolationTextResult result
    ) {
        try {
            ContentRiskRecord record = new ContentRiskRecord();
            record.setContentType(contentType);
            record.setContentId(contentId);
            record.setContentVersion(contentVersion != null ? contentVersion : 1);
            record.setRuleVersion(ruleVersion);
            record.setRiskType(result.getRiskType());
            record.setRiskLevel(result.getRiskLevel());
            record.setRiskScore(result.getRiskScore());
            record.setDetectionStatus(result.getDetectionStatus());
            record.setModelName(result.getModelName());
            record.setBusinessTraceId(result.getBusinessTraceId());

            // 序列化命中规则为 JSON
            if (result.getMatchedRules() != null && !result.getMatchedRules().isEmpty()) {
                record.setMatchedRules(objectMapper.writeValueAsString(result.getMatchedRules()));
            }

            // 生成脱敏摘要（截取文本片段）
            String excerpt = content != null ? content.replaceAll("\\s+", " ") : "";
            if (excerpt.length() > 200) {
                excerpt = excerpt.substring(0, 200) + "...";
            }
            record.setMaskedExcerpt(excerpt);

            riskRecordMapper.insert(record);

            log.info("违规检测记录已保存: contentId={}, riskLevel={}, riskScore={}, status={}",
                    contentId, result.getRiskLevel(), result.getRiskScore(),
                    result.getDetectionStatus());

        } catch (JsonProcessingException e) {
            log.error("序列化命中规则失败: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("保存违规检测记录失败: contentId={}, error={}", contentId, e.getMessage(), e);
        }
    }

    /**
     * 查询指定内容的最新检测记录。
     */
    public ContentRiskRecord getLatestRecord(String contentType, Long contentId) {
        return riskRecordMapper.findLatest(contentType, contentId);
    }

    /**
     * 查询指定内容的所有检测记录。
     */
    public List<ContentRiskRecord> getRecords(String contentType, Long contentId) {
        return riskRecordMapper.findByContent(contentType, contentId);
    }

    /**
     * 按风险类型统计（全量，不限时间 — 用于管理后台统计图表）。
     */
    public List<Map<String, Object>> getRiskTypeStatsAll() {
        return riskRecordMapper.countByRiskTypeAll();
    }

    /**
     * 按风险类型统计（限时间段 — 用于管理后台统计图表）。
     */
    public List<Map<String, Object>> getRiskTypeStats(java.time.OffsetDateTime since) {
        return riskRecordMapper.countByRiskType(since);
    }

    /**
     * 按风险等级统计（全量，不限时间 — 用于管理后台统计图表）。
     */
    public List<Map<String, Object>> getRiskLevelStatsAll() {
        return riskRecordMapper.countByRiskLevelAll();
    }

    /**
     * 按风险等级统计（限时间段 — 用于管理后台统计图表）。
     */
    public List<Map<String, Object>> getRiskLevelStats(java.time.OffsetDateTime since) {
        return riskRecordMapper.countByRiskLevel(since);
    }

    /**
     * 按检测状态统计（全量，不限时间 — 用于管理后台统计图表）。
     */
    public List<Map<String, Object>> getDetectionStatusStatsAll() {
        return riskRecordMapper.countByDetectionStatusAll();
    }

    /**
     * 按检测状态统计（限时间段 — 用于管理后台统计图表）。
     */
    public List<Map<String, Object>> getDetectionStatusStats(java.time.OffsetDateTime since) {
        return riskRecordMapper.countByDetectionStatus(since);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 解析命中规则列表。
     */
    private List<ViolationTextResult.MatchedRuleInfo> parseMatchedRules(JsonNode rulesNode) {
        List<ViolationTextResult.MatchedRuleInfo> rules = new ArrayList<>();
        if (rulesNode == null || !rulesNode.isArray()) {
            return rules;
        }

        for (JsonNode node : rulesNode) {
            try {
                rules.add(ViolationTextResult.MatchedRuleInfo.builder()
                        .ruleCode(node.path("ruleCode").asText(""))
                        .ruleName(node.path("ruleName").asText(""))
                        .riskType(node.path("riskType").asText(""))
                        .confidence(node.path("confidence").asDouble(0.0))
                        .evidenceExcerpt(node.path("evidenceExcerpt").asText(null))
                        .build());
            } catch (Exception e) {
                log.warn("跳过无效规则条目: {}", e.getMessage());
            }
        }
        return rules;
    }

    /**
     * 确保分值在 0-100 范围内。
     */
    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

}
