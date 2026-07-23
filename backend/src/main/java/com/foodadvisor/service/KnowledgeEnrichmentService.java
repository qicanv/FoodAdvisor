package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.entity.Dish;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Review;
import com.foodadvisor.mapper.DishMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.ReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识内容增强服务 — 将 DB 结构化字段拼入文本后再提交向量化入库。
 *
 * 入库前文本构建规则：
 *
 * MERCHANT_INTRO = {name}。类型: {category}。菜系: {cuisine}
 *                。环境标签: {environment_tags}。{description}
 *
 * MENU           = {dish.name}，分类: {dish.category}
 *                ，口味: {dish.tasteTags}
 *                ，价格: ¥{dish.price}。{dish.description}
 *
 * REVIEW         = 评分: {rating}/5。{content}
 */
@Service
public class KnowledgeEnrichmentService {

    private static final Logger log =
            LoggerFactory.getLogger(KnowledgeEnrichmentService.class);

    private static final String SOURCE_TYPE_MERCHANT_INTRO = "MERCHANT_INTRO";
    private static final String SOURCE_TYPE_MENU = "MENU";
    private static final String SOURCE_TYPE_REVIEW = "REVIEW";

    private static final String REVIEW_STATUS_PUBLISHED = "PUBLISHED";
    private static final String REVIEW_MODERATION_APPROVED = "APPROVED";

    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;
    private final ReviewMapper reviewMapper;
    private final AIClientService aiClientService;
    private final ObjectMapper objectMapper;

    public KnowledgeEnrichmentService(
            MerchantMapper merchantMapper,
            DishMapper dishMapper,
            ReviewMapper reviewMapper,
            AIClientService aiClientService,
            ObjectMapper objectMapper
    ) {
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
        this.reviewMapper = reviewMapper;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
    }

    // ================================================================
    // 公开方法
    // ================================================================

    /**
     * 为指定商家构建并提交 MERCHANT_INTRO 知识文档。
     *
     * @return 提交的 chunk 数量
     */
    public int syncMerchantIntro(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || !"ACTIVE".equals(merchant.getPlatformStatus())) {
            log.warn("商家不存在或非活跃状态，跳过同步: merchantId={}", merchantId);
            return 0;
        }

        String enrichedText = buildMerchantIntroText(merchant);
        log.info("构建商家介绍文本: merchantId={}, len={}", merchantId, enrichedText.length());
        log.debug("商家介绍文本: {}", enrichedText);

        return submitAndUpsert(SOURCE_TYPE_MERCHANT_INTRO, merchantId, enrichedText);
    }

    /**
     * 为指定商家下的所有有效菜品构建并提交 MENU 知识文档。
     *
     * @return 提交的 chunk 数量
     */
    public int syncMerchantMenu(Long merchantId) {
        List<Dish> dishes =
                dishMapper.selectActiveByMerchantIds(
                        List.of(merchantId)
                );

        if (dishes == null || dishes.isEmpty()) {
            log.info(
                    "商家无有效菜品，跳过菜单同步: merchantId={}",
                    merchantId
            );
            return 0;
        }

        int totalChunks = 0;

        for (Dish dish : dishes) {
            String enrichedText = buildDishText(dish);

            int chunks = submitAndUpsertWithSourceId(
                    SOURCE_TYPE_MENU,
                    merchantId,
                    dish.getId(),
                    enrichedText
            );

            totalChunks += chunks;
        }

        log.info(
                "菜品同步完成: merchantId={}, dishCount={}, totalChunks={}",
                merchantId,
                dishes.size(),
                totalChunks
        );

        return totalChunks;
    }

    /**
     * 为指定商家的所有有效评价构建并提交 REVIEW 知识文档。
     *
     * @return 提交的 chunk 数量
     */
    public int syncMerchantReviews(Long merchantId) {
        // APPROVED 状态 + 未删除 = 有效评价
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getMerchantId, merchantId)
                        .eq(Review::getStatus, REVIEW_STATUS_PUBLISHED)
                        .eq(
                                Review::getModerationStatus,
                                REVIEW_MODERATION_APPROVED
                        )
                        .isNull(Review::getDeletedAt)
        );

        if (reviews == null || reviews.isEmpty()) {
            log.info("商家无有效评价，跳过评价同步: merchantId={}", merchantId);
            return 0;
        }

        int totalChunks = 0;
        for (Review review : reviews) {
            String enrichedText = buildReviewText(review);
            int chunks = submitAndUpsertWithSourceId(
                    SOURCE_TYPE_REVIEW, merchantId, review.getId(), enrichedText);
            totalChunks += chunks;
        }

        log.info("评价同步完成: merchantId={}, reviewCount={}, totalChunks={}",
                merchantId, reviews.size(), totalChunks);
        return totalChunks;
    }

    /**
     * 全量同步一个商家：intro + menu + reviews。
     */
    public Map<String, Integer> syncMerchantAll(Long merchantId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("intro", syncMerchantIntro(merchantId));
        result.put("menu", syncMerchantMenu(merchantId));
        result.put("reviews", syncMerchantReviews(merchantId));
        return result;
    }

    // ================================================================
    // 文本构造（package-private 供测试）
    // ================================================================

    /**
     * 构建增强的商家介绍文本。
     *
     * 拼接顺序：名称 → 分类 → 菜系 → 环境标签 → description
     * 每个字段都是可选的，不存在时跳过。
     */
    String buildMerchantIntroText(Merchant m) {
        StringBuilder sb = new StringBuilder();

        // 名称（必定存在）
        sb.append(m.getName());

        // 分类
        if (notBlank(m.getCategory())) {
            sb.append("。类型：").append(m.getCategory());
        }

        // 菜系
        if (notBlank(m.getCuisine())) {
            sb.append("。菜系：").append(m.getCuisine());
        }

        // 环境标签（从 JSONB 解析）
        List<String> envTags = parseJsonArray(m.getEnvironmentTags());
        if (!envTags.isEmpty()) {
            sb.append("。环境标签：");
            sb.append(String.join("、", envTags));
        }

        // 商家描述
        if (notBlank(m.getDescription())) {
            sb.append("。").append(m.getDescription());
        }

        return sb.toString();
    }

    /**
     * 构建增强的单道菜品文本。
     */
    String buildDishText(Dish d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.getName());

        // 分类
        if (notBlank(d.getCategory())) {
            sb.append("，分类：").append(d.getCategory());
        }

        // 口味标签
        List<String> tasteTags = parseJsonArray(d.getTasteTags());
        if (!tasteTags.isEmpty()) {
            sb.append("，口味：").append(String.join("、", tasteTags));
        }

        // 价格
        if (d.getPrice() != null) {
            sb.append("，价格：¥")
                    .append(d.getPrice().stripTrailingZeros().toPlainString());
        }

        // 描述
        if (notBlank(d.getDescription())) {
            sb.append("。").append(d.getDescription());
        }

        return sb.toString();
    }

    /**
     * 构建增强的评价文本。
     */
    String buildReviewText(Review r) {
        StringBuilder sb = new StringBuilder();

        if (r.getRating() != null && r.getRating().intValue() > 0) {
            sb.append("评分：").append(r.getRating()).append("/5。");
        }

        if (notBlank(r.getContent())) {
            sb.append(r.getContent());
        }

        return sb.toString();
    }

    // ================================================================
    // 提交到 AI Service
    // ================================================================

    /**
     * 提交文本到 AI Service 的内容处理管线并写入 OpenSearch。
     */
    private int submitAndUpsert(String sourceType, Long merchantId, String text) {
        return submitAndUpsertWithSourceId(sourceType, merchantId, merchantId, text);
    }

    @SuppressWarnings("unchecked")
    private int submitAndUpsertWithSourceId(
            String sourceType, Long merchantId, Long sourceId, String text
    ) {
        try {
            // Step 1: 清洗 + 切分
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("merchantId", merchantId);
            item.put("sourceType", sourceType);
            item.put("sourceId", sourceId);
            item.put("content", text);

            JsonNode processResp = aiClientService.processContent(List.of(item));
            JsonNode chunksNode = processResp.path("chunks");
            if (!chunksNode.isArray() || chunksNode.isEmpty()) {
                return 0;
            }
            List<Map<String, Object>> chunks = objectMapper.convertValue(
                    chunksNode, new TypeReference<List<Map<String, Object>>>() {});

            // Step 2: 组装 upsert 文档
            List<Map<String, Object>> documents = new ArrayList<>();
            for (Map<String, Object> chunk : chunks) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("chunkId", chunk.get("chunkId"));
                doc.put("merchantId", chunk.get("merchantId"));
                doc.put("sourceType", chunk.get("sourceType"));
                doc.put("sourceId", chunk.get("sourceId"));
                doc.put("contentVersion", 1);
                doc.put("chunkIndex", chunk.get("chunkIndex"));
                doc.put("totalChunks", chunk.get("totalChunks"));
                doc.put("text", chunk.get("cleanedText"));
                doc.put("sourceTimestamp", chunk.get("sourceTimestamp"));
                documents.add(doc);
            }

            // Step 3: 向量化 + 写入 OpenSearch
            aiClientService.upsertKnowledge(documents);
            return chunks.size();
        } catch (Exception e) {
            log.error("知识入库失败: sourceType={}, merchantId={}, sourceId={}, error={}",
                    sourceType, merchantId, sourceId, e.getMessage(), e);
            return 0;
        }
    }

    // ================================================================
    // 辅助
    // ================================================================

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.debug("JSON 数组解析失败，忽略: {}", json);
            return List.of();
        }
    }
}
