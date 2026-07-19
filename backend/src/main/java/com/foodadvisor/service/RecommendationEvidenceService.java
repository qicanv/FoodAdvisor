package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.recommendation.RecommendationEvidenceDetailVO;
import com.foodadvisor.entity.*;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationEvidenceService {
    private static final int MAX_TEXT = 800;
    private final RecommendationMapper recommendationMapper;
    private final RecommendationItemMapper itemMapper;
    private final RecommendationEvidenceMapper evidenceMapper;
    private final ReviewMapper reviewMapper;
    private final MerchantMapper merchantMapper;
    private final ObjectMapper objectMapper;

    public RecommendationEvidenceService(
            RecommendationMapper recommendationMapper,
            RecommendationItemMapper itemMapper,
            RecommendationEvidenceMapper evidenceMapper,
            ReviewMapper reviewMapper,
            MerchantMapper merchantMapper,
            ObjectMapper objectMapper) {
        this.recommendationMapper = recommendationMapper;
        this.itemMapper = itemMapper;
        this.evidenceMapper = evidenceMapper;
        this.reviewMapper = reviewMapper;
        this.merchantMapper = merchantMapper;
        this.objectMapper = objectMapper;
    }

    public List<RecommendationEvidenceDetailVO> list(
            Long userId, Long recommendationId, Long merchantId) {
        Recommendation recommendation = recommendationMapper.selectById(recommendationId);
        if (recommendation == null
                || !Objects.equals(userId, recommendation.getUserId())) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "RECOMMENDATION_NOT_FOUND", "推荐记录不存在");
        }
        List<RecommendationItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<RecommendationItem>()
                        .eq(RecommendationItem::getRecommendationId, recommendationId)
                        .eq(RecommendationItem::getMerchantId, merchantId));
        if (items == null || items.isEmpty()) return List.of();
        Map<Long, Long> itemMerchants = items.stream().collect(Collectors.toMap(
                RecommendationItem::getId, RecommendationItem::getMerchantId));
        Merchant merchant = merchantMapper.selectById(merchantId);
        Map<Long, Merchant> merchants = merchant == null
                ? Map.of() : Map.of(merchantId, merchant);
        List<RecommendationEvidence> evidences = evidenceMapper.selectList(
                new LambdaQueryWrapper<RecommendationEvidence>()
                        .in(RecommendationEvidence::getRecommendationItemId,
                                new ArrayList<>(itemMerchants.keySet()))
                        .orderByAsc(RecommendationEvidence::getId));
        List<Long> reviewIds = safe(evidences).stream()
                .filter(e -> "REVIEW".equals(e.getSourceType()))
                .map(RecommendationEvidence::getReviewId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Review> reviews = reviewIds.isEmpty() ? Map.of()
                : reviewMapper.selectByIds(reviewIds).stream()
                .collect(Collectors.toMap(Review::getId, review -> review));
        List<RecommendationEvidenceDetailVO> result = new ArrayList<>();
        for (RecommendationEvidence evidence : safe(evidences)) {
            Long evidenceMerchantId =
                    itemMerchants.get(evidence.getRecommendationItemId());
            if (evidenceMerchantId == null
                    || !evidenceMerchantId.equals(
                    evidence.getSourceMerchantId())) continue;
            RecommendationEvidenceDetailVO vo = new RecommendationEvidenceDetailVO();
            vo.setSourceType(evidence.getSourceType());
            vo.setMerchantId(evidenceMerchantId);
            vo.setConditionKey(evidence.getConditionKey());
            vo.setMerchantName(Optional.ofNullable(
                            merchants.get(evidenceMerchantId))
                    .map(Merchant::getName).orElse("商家"));
            vo.setAvailable(true);
            if ("REVIEW".equals(evidence.getSourceType())) {
                Review review = reviews.get(evidence.getReviewId());
                if (review != null
                        && !evidenceMerchantId.equals(
                        review.getMerchantId())) {
                    continue;
                }
                boolean available = review != null
                        && "PUBLISHED".equals(review.getStatus())
                        && "APPROVED".equals(review.getModerationStatus())
                        && review.getDeletedAt() == null;
                vo.setAvailable(available);
                vo.setSourceId(evidence.getReviewId());
                if (available) {
                    vo.setExcerpt(limit(review.getContent()));
                    vo.setReviewTime(review.getPublishedAt() != null
                            ? review.getPublishedAt() : review.getReviewTime());
                    vo.setHighlightTitle(readTitle(evidence.getSourceTextSnapshot()));
                } else {
                    vo.setUnavailableReason("SOURCE_UNAVAILABLE");
                }
            } else if ("DISH".equals(evidence.getSourceType())) {
                vo.setExcerpt(readDishText(evidence.getSourceTextSnapshot(),
                        evidence.getEvidenceExcerpt()));
            } else {
                vo.setExcerpt(limit(evidence.getEvidenceExcerpt()));
            }
            result.add(vo);
        }
        return result;
    }

    private String readTitle(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path("title").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }
    private String readDishText(String json, String fallback) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String name = node.path("dishName").asText("");
            String price = node.path("dishPrice").isMissingNode()
                    || node.path("dishPrice").isNull()
                    ? "" : "，当次价格 ¥" + node.path("dishPrice").asText();
            return limit(("菜单菜品：" + name + price).trim());
        } catch (Exception ignored) {
            return limit(fallback);
        }
    }
    private String limit(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), MAX_TEXT));
    }
    private <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }
}
