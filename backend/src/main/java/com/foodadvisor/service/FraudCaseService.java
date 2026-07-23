package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.fraud.FraudCaseDetailVO;
import com.foodadvisor.dto.fraud.FraudCaseListVO;
import com.foodadvisor.dto.fraud.ReviewRequest;
import com.foodadvisor.entity.ReviewFraudCase;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.FraudCaseQueryMapper;
import com.foodadvisor.mapper.ReviewFraudCaseMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 刷评案例管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudCaseService {

    private final FraudCaseQueryMapper queryMapper;
    private final ReviewFraudCaseMapper fraudCaseMapper;
    private final ReviewMapper reviewMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    // ---- 规则类型 / 状态 / 结论 中文映射 ----

    private static final Map<String, String> RULE_TYPE_TEXT = Map.of(
            "CONCENTRATION", "集中评价",
            "SIMILARITY", "文本相似",
            "FREQUENCY", "频繁评价",
            "RATING_ANOMALY", "评分异常"
    );

    private static final Map<String, String> STATUS_TEXT = Map.of(
            "SUSPICIOUS", "疑似刷评",
            "PENDING_REVIEW", "待复核",
            "REVIEWED", "已复核",
            "DISMISSED", "已排除"
    );

    private static final Map<String, String> CONCLUSION_TEXT = Map.of(
            "CONFIRMED_FRAUD", "确认刷评",
            "DISMISSED", "排除嫌疑",
            "NEED_FURTHER_CHECK", "需进一步调查"
    );

    // ---- 列表 ----

    public Map<String, Object> listCases(String status, String riskLevel, String ruleType,
                                            Long merchantId, OffsetDateTime startTime,
                                            OffsetDateTime endTime, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;

        // 用 FraudCaseQueryMapper（纯 MyBatis，不触发 MP 实体解析）
        List<Map<String, Object>> rows = queryMapper.findCaseMaps(
                blankToNull(status), blankToNull(riskLevel), blankToNull(ruleType),
                merchantId, pageSize, offset);

        long total = queryMapper.countCases(
                blankToNull(status), blankToNull(riskLevel), blankToNull(ruleType),
                merchantId);

        Set<Long> merchantIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object mid = row.get("merchant_id");
            if (mid instanceof Number n) merchantIds.add(n.longValue());
        }
        Map<Long, String> merchantNames = loadMerchantNames(merchantIds);

        List<FraudCaseListVO> vos = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            vos.add(toListVO(row, merchantNames));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("records", vos);
        return result;
    }

    private String blankToNull(String s) {
        return (s != null && !s.isBlank()) ? s : null;
    }

    /** 从 Map 行构建列表 VO */
    private FraudCaseListVO toListVO(Map<String, Object> row, Map<Long, String> merchantNames) {
        Long merchantId = toLong(row.get("merchant_id"));
        String ruleType = (String) row.get("rule_type");
        String status = (String) row.get("status");

        // JSONB 字段在 SQL 中用 ::text 已转为 String
        List<Long> reviewIds = parseReviewIds((String) row.get("matched_review_ids"));
        Object snapshot = parseJsonSafely((String) row.get("matched_rule_snapshot"));

        String reviewConclusion = (String) row.get("review_conclusion");
        return FraudCaseListVO.builder()
                .caseId(toLong(row.get("case_id")))
                .merchantId(merchantId)
                .merchantName(merchantNames.getOrDefault(merchantId, "未知商家"))
                .ruleType(ruleType)
                .ruleTypeText(ruleType != null
                        ? RULE_TYPE_TEXT.getOrDefault(ruleType, ruleType) : null)
                .riskLevel((String) row.get("risk_level"))
                .status(status)
                .statusText(status != null
                        ? STATUS_TEXT.getOrDefault(status, status) : null)
                .matchedRuleSnapshot(snapshot)
                .matchedReviewIds(reviewIds)
                .relatedReviewCount(reviewIds.size())
                .summary((String) row.get("summary"))
                .detectedAt(toOffsetDateTime(row.get("detected_at")))
                .reviewedByName(null)
                .reviewedAt(toOffsetDateTime(row.get("reviewed_at")))
                .reviewConclusion(reviewConclusion)
                .reviewConclusionText(reviewConclusion != null
                        ? CONCLUSION_TEXT.getOrDefault(reviewConclusion, null) : null)
                .build();
    }

    /** JSONB 字段 JDBC 返回 PGobject，需转 String */
    private String jsonbString(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return null;
        return v.toString();
    }

    private Long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private OffsetDateTime toOffsetDateTime(Object v) {
        if (v instanceof OffsetDateTime odt) return odt;
        return null;
    }

    // ---- 详情 ----

    public FraudCaseDetailVO getCaseDetail(Long caseId) {
        ReviewFraudCase c = fraudCaseMapper.selectById(caseId);
        if (c == null) {
            return null;
        }

        Object snapshot = parseJsonSafely(c.getMatchedRuleSnapshot());
        List<Long> reviewIds = parseReviewIds(c.getMatchedReviewIds());

        // 查询关联评价
        List<FraudCaseDetailVO.RelatedReview> relatedReviews = loadRelatedReviews(reviewIds);

        Map<Long, String> merchantNames = loadMerchantNames(Set.of(c.getMerchantId()));

        String conclusion = c.getReviewConclusion();
        String ruleType = c.getRuleType();
        String status = c.getStatus();

        // 复核历史
        FraudCaseDetailVO.ReviewHistory history = FraudCaseDetailVO.ReviewHistory.builder()
                .reviewedBy(c.getReviewedBy())
                .reviewedByName(null)
                .reviewedAt(c.getReviewedAt())
                .reviewConclusion(conclusion)
                .reviewConclusionText(conclusion != null
                        ? CONCLUSION_TEXT.getOrDefault(conclusion, null) : null)
                .reviewRemark(c.getReviewRemark())
                .build();

        return FraudCaseDetailVO.builder()
                .caseId(c.getId())
                .merchantId(c.getMerchantId())
                .merchantName(merchantNames.getOrDefault(c.getMerchantId(), "未知商家"))
                .ruleType(ruleType)
                .ruleTypeText(ruleType != null
                        ? RULE_TYPE_TEXT.getOrDefault(ruleType, ruleType) : null)
                .riskLevel(c.getRiskLevel())
                .status(status)
                .statusText(status != null
                        ? STATUS_TEXT.getOrDefault(status, status) : null)
                .matchedRuleSnapshot(snapshot)
                .summary(c.getSummary())
                .relatedReviews(relatedReviews)
                .reviewHistory(history)
                .detectedAt(c.getDetectedAt())
                .build();
    }

    // ---- 人工复核 ----

    @Transactional
    public ReviewFraudCase submitReview(Long caseId, ReviewRequest request,
                                         Long operatorUserId, String operatorUsername) {
        ReviewFraudCase c = fraudCaseMapper.selectById(caseId);
        if (c == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "案例不存在");
        }
        String currentStatus = c.getStatus();
        if ("REVIEWED".equals(currentStatus) || "DISMISSED".equals(currentStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "STATUS_CONFLICT",
                    "该案例已处理完成，不允许重复复核");
        }

        String conclusion = request.getConclusion().toUpperCase();
        String newStatus;
        switch (conclusion) {
            case "CONFIRMED_FRAUD":
                newStatus = "REVIEWED";
                break;
            case "DISMISSED":
                newStatus = "DISMISSED";
                break;
            case "NEED_FURTHER_CHECK":
                newStatus = "PENDING_REVIEW";
                break;
            default:
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONCLUSION",
                        "无效的复核结论: " + conclusion);
        }

        c.setStatus(newStatus);
        c.setReviewConclusion(conclusion);
        c.setReviewRemark(request.getRemark());
        c.setReviewedBy(operatorUserId);
        c.setReviewedAt(OffsetDateTime.now());

        fraudCaseMapper.updateById(c);

        // 确认刷评 → 批量隐藏关联评价
        if ("CONFIRMED_FRAUD".equals(conclusion)) {
            List<Long> reviewIds = parseReviewIds(c.getMatchedReviewIds());
            if (!reviewIds.isEmpty()) {
                reviewMapper.batchUpdateReviewStatus(reviewIds, "HIDDEN");
            }
        }

        // 审计日志
        auditLogService.recordSafely(buildAuditLog(caseId, conclusion, request.getRemark(),
                operatorUserId, operatorUsername));

        return c;
    }

    // ---- 辅助方法 ----

    List<Long> parseReviewIds(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse matchedReviewIds: {}", json, e);
            return List.of();
        }
    }

    private Object parseJsonSafely(String json) {
        try {
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", json, e);
            return json;
        }
    }

    private Map<Long, String> loadMerchantNames(Set<Long> merchantIds) {
        if (merchantIds.isEmpty()) return Map.of();
        // 一次性查询所有活跃商家，避免N+1问题
        List<Map<String, Object>> allMerchants = reviewMapper.getActiveMerchants();
        Map<Long, String> nameMap = new HashMap<>();
        for (Map<String, Object> m : allMerchants) {
            Long mId = ((Number) m.get("id")).longValue();
            if (merchantIds.contains(mId)) {
                nameMap.put(mId, (String) m.get("name"));
            }
        }
        return nameMap;
    }

    private List<FraudCaseDetailVO.RelatedReview> loadRelatedReviews(List<Long> reviewIds) {
        if (reviewIds.isEmpty()) return List.of();
        List<FraudCaseDetailVO.RelatedReview> result = new ArrayList<>();
        for (Long reviewId : reviewIds) {
            Map<String, Object> detail = reviewMapper.getReviewDetailWithRelations(reviewId);
            if (detail != null) {
                result.add(FraudCaseDetailVO.RelatedReview.builder()
                        .reviewId(reviewId)
                        .userId(detail.get("user_id") instanceof Number n ? n.longValue() : null)
                        .userNickname((String) detail.get("user_nickname"))
                        .rating(detail.get("rating") instanceof Number n ? n.doubleValue() : null)
                        .content((String) detail.get("content"))
                        .reviewStatus((String) detail.get("status"))
                        .riskLevel((String) detail.get("risk_level"))
                        .createdAt(detail.get("created_at") instanceof java.time.OffsetDateTime odt
                                ? odt : null)
                        .build());
            }
        }
        return result;
    }

    private com.foodadvisor.entity.AuditLog buildAuditLog(Long caseId, String conclusion,
                                                           String remark, Long operatorUserId,
                                                           String operatorUsername) {
        com.foodadvisor.entity.AuditLog log = new com.foodadvisor.entity.AuditLog();
        log.setOperationType("FRAUD_REVIEW");
        log.setOperatorUserId(operatorUserId);
        log.setOperatorUsername(operatorUsername);
        log.setOperatorRole("ADMIN");
        log.setModule("FRAUD_DETECTION");
        log.setLevel("INFO");
        log.setResult("SUCCESS");
        log.setObjectType("REVIEW_FRAUD_CASE");
        log.setObjectId(String.valueOf(caseId));
        log.setMetadata("{\"conclusion\":\"" + conclusion + "\",\"remark\":\""
                + (remark != null ? remark.replace("\"", "\\\"") : "") + "\"}");
        return log;
    }

    /** 批量修改评论状态 */
    public int batchUpdateReviewStatus(List<Long> reviewIds, String newStatus) {
        if (reviewIds == null || reviewIds.isEmpty()) return 0;
        return reviewMapper.batchUpdateReviewStatus(reviewIds, newStatus);
    }
}
