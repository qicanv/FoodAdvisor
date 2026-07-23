package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodadvisor.dto.fraud.FraudCaseDetailVO;
import com.foodadvisor.dto.fraud.FraudCaseListVO;
import com.foodadvisor.dto.fraud.ReviewRequest;
import com.foodadvisor.entity.ReviewFraudCase;
import com.foodadvisor.exception.ApiException;
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
public class FraudCaseService extends ServiceImpl<ReviewFraudCaseMapper, ReviewFraudCase> {

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

    public Page<FraudCaseListVO> listCases(String status, String riskLevel, String ruleType,
                                            Long merchantId, OffsetDateTime startTime,
                                            OffsetDateTime endTime, int pageNum, int pageSize) {
        LambdaQueryWrapper<ReviewFraudCase> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(ReviewFraudCase::getStatus, status);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            wrapper.eq(ReviewFraudCase::getRiskLevel, riskLevel);
        }
        if (ruleType != null && !ruleType.isBlank()) {
            wrapper.eq(ReviewFraudCase::getRuleType, ruleType);
        }
        if (merchantId != null) {
            wrapper.eq(ReviewFraudCase::getMerchantId, merchantId);
        }
        if (startTime != null) {
            wrapper.ge(ReviewFraudCase::getDetectedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(ReviewFraudCase::getDetectedAt, endTime);
        }
        wrapper.orderByDesc(ReviewFraudCase::getDetectedAt);

        Page<ReviewFraudCase> page = fraudCaseMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        // 收集所有 merchantId 批量查询商家名称
        Set<Long> merchantIds = page.getRecords().stream()
                .map(ReviewFraudCase::getMerchantId)
                .collect(Collectors.toSet());
        Map<Long, String> merchantNames = loadMerchantNames(merchantIds);

        List<FraudCaseListVO> vos = page.getRecords().stream()
                .map(c -> toListVO(c, merchantNames))
                .collect(Collectors.toList());

        Page<FraudCaseListVO> resultPage = new Page<>(pageNum, pageSize, page.getTotal());
        resultPage.setRecords(vos);
        return resultPage;
    }

    private FraudCaseListVO toListVO(ReviewFraudCase c, Map<Long, String> merchantNames) {
        List<Long> reviewIds = parseReviewIds(c.getMatchedReviewIds());
        Object snapshot = parseJsonSafely(c.getMatchedRuleSnapshot());

        return FraudCaseListVO.builder()
                .caseId(c.getId())
                .merchantId(c.getMerchantId())
                .merchantName(merchantNames.getOrDefault(c.getMerchantId(), "未知商家"))
                .ruleType(c.getRuleType())
                .ruleTypeText(RULE_TYPE_TEXT.getOrDefault(c.getRuleType(), c.getRuleType()))
                .riskLevel(c.getRiskLevel())
                .status(c.getStatus())
                .statusText(STATUS_TEXT.getOrDefault(c.getStatus(), c.getStatus()))
                .matchedRuleSnapshot(snapshot)
                .relatedReviewCount(reviewIds.size())
                .summary(c.getSummary())
                .detectedAt(c.getDetectedAt())
                .reviewedByName(null) // 详情页可单独加载
                .reviewedAt(c.getReviewedAt())
                .reviewConclusion(c.getReviewConclusion())
                .reviewConclusionText(CONCLUSION_TEXT.getOrDefault(c.getReviewConclusion(), null))
                .build();
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

        // 复核历史
        FraudCaseDetailVO.ReviewHistory history = FraudCaseDetailVO.ReviewHistory.builder()
                .reviewedBy(c.getReviewedBy())
                .reviewedByName(null)
                .reviewedAt(c.getReviewedAt())
                .reviewConclusion(c.getReviewConclusion())
                .reviewConclusionText(CONCLUSION_TEXT.getOrDefault(c.getReviewConclusion(), null))
                .reviewRemark(c.getReviewRemark())
                .build();

        return FraudCaseDetailVO.builder()
                .caseId(c.getId())
                .merchantId(c.getMerchantId())
                .merchantName(merchantNames.getOrDefault(c.getMerchantId(), "未知商家"))
                .ruleType(c.getRuleType())
                .ruleTypeText(RULE_TYPE_TEXT.getOrDefault(c.getRuleType(), c.getRuleType()))
                .riskLevel(c.getRiskLevel())
                .status(c.getStatus())
                .statusText(STATUS_TEXT.getOrDefault(c.getStatus(), c.getStatus()))
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
        if (!"PENDING_REVIEW".equals(c.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATUS_CONFLICT",
                    "该案例当前状态为" + STATUS_TEXT.getOrDefault(c.getStatus(), c.getStatus()) + "，不允许重复复核");
        }

        String conclusion = request.getConclusion().toUpperCase();
        String newStatus;
        switch (conclusion) {
            case "CONFIRMED_FRAUD":
            case "DISMISSED":
                newStatus = "REVIEWED";
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
        // 复用 ReviewMapper 中的 getActiveMerchants 或者直接写一个简单查询
        // 这里用简单的方式：逐条查
        Map<Long, String> result = new HashMap<>();
        for (Long id : merchantIds) {
            List<Map<String, Object>> merchants = reviewMapper.getActiveMerchants();
            for (Map<String, Object> m : merchants) {
                Long mId = ((Number) m.get("id")).longValue();
                if (mId.equals(id)) {
                    result.put(id, (String) m.get("name"));
                    break;
                }
            }
        }
        return result;
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
}
