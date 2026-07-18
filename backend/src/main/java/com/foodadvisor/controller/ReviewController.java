package com.foodadvisor.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.ReviewAnalysisResultVO;
import com.foodadvisor.dto.review.MyReviewDetailVO;
import com.foodadvisor.dto.review.MyReviewListVO;
import com.foodadvisor.dto.review.ReviewDisplayVO;
import com.foodadvisor.dto.review.ReviewFollowUpRequest;
import com.foodadvisor.dto.review.ReviewFollowUpVO;
import com.foodadvisor.dto.review.ReviewReplyVO;
import com.foodadvisor.dto.review.ReviewSubmitRequest;
import com.foodadvisor.dto.review.ReviewSubmitResponse;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.service.AIClientService;
import com.foodadvisor.service.ReviewService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.review.ReviewTagStatVO;
import com.foodadvisor.entity.ReviewTag;
import com.foodadvisor.entity.ReviewTagRelation;
import com.foodadvisor.mapper.ReviewTagMapper;
import com.foodadvisor.mapper.TagRelationWithName;
import com.foodadvisor.entity.ReviewIssueCategory;
import com.foodadvisor.entity.ReviewIssueRelation;
import com.foodadvisor.mapper.ReviewIssueCategoryMapper;
import com.foodadvisor.dto.IssueStatVO;
import com.foodadvisor.dto.IssueReviewVO;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/**
 * 评价接口 — 评论查询 & AI 分析
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final AIClientService aiClientService;
    private final ReviewTagMapper reviewTagMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ReviewIssueCategoryMapper issueCategoryMapper;

    public ReviewController(
            ReviewService reviewService,
            AIClientService aiClientService,
            ReviewTagMapper reviewTagMapper,
            JdbcTemplate jdbcTemplate,
            ReviewIssueCategoryMapper issueCategoryMapper
    ) {
        this.reviewService = reviewService;
        this.aiClientService = aiClientService;
        this.reviewTagMapper = reviewTagMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.issueCategoryMapper = issueCategoryMapper;
    }


    /**
     * 按商家分页查询评价
     * 支持可选的标签和情感倾向筛选（EPIC-01 Story 8）。
     * @param merchantId 商家 ID
     * @param pageNum    页码，默认 1
     * @param pageSize   每页条数，默认 10
     * @param tagCode    可选，按标签编码筛选（如 TASTE_GOOD）
     * @param sentiment  可选，按情感倾向筛选（POSITIVE / NEGATIVE / NEUTRAL）
     *                   仅在 tagCode 不为空时生效
     */
    @GetMapping
    public ApiResponse<PageResult<ReviewDisplayVO>> list(
            @RequestParam Long merchantId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String tagCode,
            @RequestParam(required = false) String sentiment
            ) {
        Page<ReviewDisplayVO> page = reviewService.listByMerchantWithUser(
                merchantId, pageNum, pageSize, tagCode, sentiment);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 触发单条评价的 AI 分析（调用 FastAPI）（V0.3 更新）
     */
    @PostMapping("/{reviewId}/analyze")
    public ApiResponse<ReviewAnalysisResultVO> analyze(@PathVariable Long reviewId) {
        Review review = reviewService.getById(reviewId);
        if (review == null) {
            return ApiResponse.notFound("评价不存在");
        }

        int reviewVersion = review.getCurrentVersion() != null ? review.getCurrentVersion() : 1;

        // 调用 FastAPI 分析
        JsonNode result = aiClientService.analyzeReview(
                review.getId(), review.getMerchantId(), review.getContent(), reviewVersion);

        // 保存分析结果到数据库
        ReviewAnalysis analysis = new ReviewAnalysis();
        analysis.setReviewId(review.getId());
        analysis.setReviewVersion(reviewVersion);
        analysis.setAnalysisVersion(result.has("analysisVersion") ? result.get("analysisVersion").asInt() : 1);
        analysis.setSentiment(result.get("sentiment").asText());
        analysis.setConfidence(new BigDecimal(result.get("confidence").asText()));
        analysis.setLowConfidence(result.has("lowConfidence") ? result.get("lowConfidence").asBoolean() : false);
        analysis.setKeywords(result.get("keywords").toString());
        analysis.setAspects(result.get("aspects").toString());
        if (result.has("negativeReason") && !result.get("negativeReason").isNull()) {
            analysis.setNegativeReason(result.get("negativeReason").asText());
        }
        analysis.setModelName(result.has("modelName") ? result.get("modelName").asText() : null);
        analysis.setModelVersion(result.has("modelVersion") && !result.get("modelVersion").isNull()
                ? result.get("modelVersion").asText() : null);
        analysis.setBusinessTraceId(result.has("businessTraceId")
                ? result.get("businessTraceId").asText() : null);
        analysis.setStatus(result.has("status") ? result.get("status").asText() : "SUCCESS");
        if (result.has("errorMessage") && !result.get("errorMessage").isNull()) {
            analysis.setErrorMessage(result.get("errorMessage").asText());
        }
        reviewService.saveAnalysis(analysis);

// ====== 持久化评价标签关联======
// AI 分析结果中的 tags 字段包含了从评论中提取的标签列表。
// 需要把这些标签关联写入 review_tag_relations 表，
// 这样后续的标签统计和筛选功能才有数据可用。
if (result.has("tags") && !result.get("tags").isNull() && result.get("tags").isArray()) {
    List<ReviewTagRelation> relations = new ArrayList<>();

    for (JsonNode tagNode : result.get("tags")) {
        String tagCode = tagNode.has("tagCode")
                ? tagNode.get("tagCode").asText()
                : null;

        if (tagCode == null || tagCode.isBlank()) {
            continue;  // 跳过没有 tagCode 的异常数据
        }

        // 通过 tagCode 从 review_tags 字典表找到对应的标签 ID
        // 只有字典中存在的标签才会被保存
        ReviewTag tag = reviewTagMapper.selectOne(
                new LambdaQueryWrapper<ReviewTag>()
                        .eq(ReviewTag::getCode, tagCode)
                        .eq(ReviewTag::getStatus, "ACTIVE")
        );

        if (tag == null) {
            continue;  // 标签编码不在预定义字典中，丢弃
        }

        // 构建关联记录
        ReviewTagRelation relation = new ReviewTagRelation();
        relation.setReviewId(review.getId());
        relation.setReviewVersion(reviewVersion);
        relation.setTagId(tag.getId());
        relation.setSentiment(
                tagNode.has("sentiment")
                        ? tagNode.get("sentiment").asText().toUpperCase()
                        : "NEUTRAL"
        );
        relation.setConfidence(
                tagNode.has("confidence")
                        ? new BigDecimal(tagNode.get("confidence").asText())
                        : BigDecimal.valueOf(0.5)
        );
        relation.setEvidenceText(
                tagNode.has("evidenceText") && !tagNode.get("evidenceText").isNull()
                        ? tagNode.get("evidenceText").asText()
                        : null
        );
        relation.setModelName(
                tagNode.has("modelName") && !tagNode.get("modelName").isNull()
                        ? tagNode.get("modelName").asText()
                        : null
        );

        relations.add(relation);
    }

    // 批量保存：先删旧关联再插入新的
    if (!relations.isEmpty()) {
        reviewService.saveTagRelations(relations);
    }
}

        // ====== 持久化差评归因关联（V0.3）======
        // AI 分析结果中的 issueCategories 包含每条差评的问题归类。
        // 需要写入 review_issue_relations 表，供商家端差评归因统计使用。
        if (result.has("issueCategories") && !result.get("issueCategories").isNull()
                && result.get("issueCategories").isArray()) {
            List<ReviewIssueRelation> issueRelations = new ArrayList<>();

            for (JsonNode issueNode : result.get("issueCategories")) {
                String catCode = issueNode.has("category")
                        ? issueNode.get("category").asText()
                        : null;

                if (catCode == null || catCode.isBlank()) {
                    continue;
                }

                // 通过类别编码查字典表取得 ID
                ReviewIssueCategory cat = issueCategoryMapper.selectOne(
                        new LambdaQueryWrapper<ReviewIssueCategory>()
                                .eq(ReviewIssueCategory::getCode, catCode)
                                .eq(ReviewIssueCategory::getStatus, "ACTIVE")
                );

                if (cat == null) {
                    continue; // 不在预定义类别中，跳过
                }

                ReviewIssueRelation relation = new ReviewIssueRelation();
                relation.setReviewId(review.getId());
                relation.setReviewVersion(reviewVersion);
                relation.setIssueCategoryId(cat.getId());
                relation.setConfidence(
                        issueNode.has("confidence")
                                ? new BigDecimal(issueNode.get("confidence").asText())
                                : BigDecimal.valueOf(0.5)
                );
                relation.setEvidenceText(
                        issueNode.has("evidenceText")
                                && !issueNode.get("evidenceText").isNull()
                                ? issueNode.get("evidenceText").asText()
                                : null
                );

                issueRelations.add(relation);
            }

            if (!issueRelations.isEmpty()) {
                reviewService.saveIssueRelations(issueRelations);
            }
        }


        // 构建返回 VO
        ReviewAnalysisResultVO vo = buildResultVO(review, result);
        return ApiResponse.success(vo);
    }

    /**
     * 构建分析结果 VO（V0.3）
     */
    private ReviewAnalysisResultVO buildResultVO(Review review, JsonNode result) {
        ReviewAnalysisResultVO vo = new ReviewAnalysisResultVO();
        vo.setReviewId(review.getId());
        vo.setMerchantId(review.getMerchantId());
        vo.setReviewVersion(result.has("reviewVersion") ? result.get("reviewVersion").asInt() : 1);
        vo.setAnalysisVersion(result.has("analysisVersion") ? result.get("analysisVersion").asInt() : 1);
        vo.setSentiment(result.get("sentiment").asText());
        vo.setConfidence(new BigDecimal(result.get("confidence").asText()));
        vo.setLowConfidence(result.has("lowConfidence") ? result.get("lowConfidence").asBoolean() : false);
        vo.setBusinessTraceId(result.has("businessTraceId") && !result.get("businessTraceId").isNull()
                ? result.get("businessTraceId").asText() : null);

        // 解析 JSON 字段
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            vo.setKeywords(mapper.readValue(result.get("keywords").toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            vo.setAspects(mapper.readValue(result.get("aspects").toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ReviewAnalysisResultVO.AspectVO>>() {}));
            if (result.has("tags") && !result.get("tags").isNull()) {
                vo.setTags(mapper.readValue(result.get("tags").toString(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<ReviewAnalysisResultVO.TagResultVO>>() {}));
            }
            if (result.has("issueCategories") && !result.get("issueCategories").isNull()) {
                vo.setIssueCategories(mapper.readValue(result.get("issueCategories").toString(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<ReviewAnalysisResultVO.IssueCategoryVO>>() {}));
            }
        } catch (Exception ignored) {
            vo.setKeywords(new ArrayList<>());
            vo.setAspects(new ArrayList<>());
            vo.setTags(new ArrayList<>());
            vo.setIssueCategories(new ArrayList<>());
        }

        if (result.has("negativeReason") && !result.get("negativeReason").isNull()) {
            vo.setNegativeReason(result.get("negativeReason").asText());
        }
        vo.setModelName(result.has("modelName") ? result.get("modelName").asText() : null);
        vo.setModelVersion(result.has("modelVersion") && !result.get("modelVersion").isNull()
                ? result.get("modelVersion").asText() : null);

        return vo;
    }

    /**
     * 获取评价的分析结果
     */
    @GetMapping("/{reviewId}/analysis")
    public ApiResponse<ReviewAnalysis> getAnalysis(@PathVariable Long reviewId) {
        ReviewAnalysis analysis = reviewService.getAnalysis(reviewId);
        return analysis != null ? ApiResponse.success(analysis) : ApiResponse.notFound("该评价尚未分析");
    }

    /**
     * 批量触发评价分析（用于种子数据批量分析）
     */
    @PostMapping("/batch-analyze")
    public ApiResponse<Integer> batchAnalyze(@RequestParam Long merchantId) {
        Page<Review> page = reviewService.listByMerchant(merchantId, 1, 100);
        int count = 0;
        for (Review review : page.getRecords()) {
            // 跳过已分析的
            if (reviewService.getAnalysis(review.getId()) != null) continue;
            try {
                analyze(review.getId());
                count++;
            } catch (Exception ignored) {
                // 单条失败不影响批量
            }
        }
        return ApiResponse.success("分析完成", count);
    }

    // ==================== 标签统计与筛选（EPIC-01 Story 8） ====================

    /**
    * 获取某个商家的评价标签统计（公开接口，不需要登录）。
    *
    * 返回该商家所有公开评价中出现的标签，以及每个标签的：
    * - 正面/中性/负面评价数量
    * - 总评价数量
    *
    * 前端用这个数据渲染标签筛选栏，用户点击标签后调用
    * GET /api/reviews?merchantId=xxx&tagCode=TASTE_GOOD 来筛选评价。
    *
    * 只有至少关联一条公开评价的标签才会被返回，
    * 没有评价支撑的标签不会出现
    */
    @GetMapping("/tags")
    public ApiResponse<List<ReviewTagStatVO>> getMerchantReviewTags(
        @RequestParam Long merchantId
    ) {
    List<ReviewTagStatVO> tags = reviewService.getMerchantReviewTags(merchantId);
    return ApiResponse.success(tags);
    }

    /**
    * 获取单条评价关联的标签（供评价列表使用）。
    */
    @GetMapping("/{reviewId}/tags")
    public ApiResponse<List<TagRelationWithName>> getReviewTags(
        @PathVariable Long reviewId
    ) {
    List<TagRelationWithName> tags = reviewService.getReviewTags(reviewId);
    return ApiResponse.success(tags);
    }


    // ==================== 评论编辑与删除 ====================

    /**
     * 编辑评价（支持图片）—— 仅允许作者修改自己的评价。
     * 前端用 multipart/form-data 格式提交。
     */
    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewSubmitResponse> edit(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestPart("request") ReviewSubmitRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        ReviewSubmitResponse response = reviewService.editReview(
                userId, reviewId, request,
                images != null ? images : List.of()
        );
        return ApiResponse.success(response);
    }

    /**
     * 编辑评价（简单版，仅文字和评分）—— 仅允许作者修改自己的评价。
     * 前端用 application/json 格式提交。
     */
    @PutMapping("/{reviewId}/simple")
    public ApiResponse<ReviewSubmitResponse> editSimple(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody ReviewSubmitRequest request
    ) {
        ReviewSubmitResponse response = reviewService.editReviewSimple(
                userId, reviewId, request
        );
        return ApiResponse.success(response);
    }

    /**
     * 删除评价 —— 逻辑删除（标记 status = "DELETED"），不物理删除数据。
     */
    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> delete(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        reviewService.deleteReview(userId, reviewId);
        return ApiResponse.success("评价已删除", null);
    }

    /**
     * 商家回复评价 —— 仅允许商家回复自己店铺的评价。
     */
    @PostMapping("/{reviewId}/reply")
    public ApiResponse<ReviewReplyVO> reply(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long merchantId,
            @RequestBody Map<String, String> request
    ) {
        String replyContent = request.get("replyContent");
        if (replyContent == null || replyContent.isBlank()) {
            return ApiResponse.failure("INVALID_REQUEST", "回复内容不能为空");
        }
        ReviewReplyVO response = reviewService.replyReview(merchantId, reviewId, replyContent);
        return ApiResponse.success(response);
    }

    /**
     * 查询当前用户的评价列表（"我的评价"页面用）。
     */
    @GetMapping("/my-reviews")
    public ApiResponse<PageResult<MyReviewListVO>> myReviews(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer rating
    ) {
        Page<MyReviewListVO> page = reviewService.listMyReviews(
                userId, pageNum, pageSize, status, rating
        );
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 获取当前用户的评价详情（含图片、版本历史）。
     */
    @GetMapping("/my-reviews/{reviewId}")
    public ApiResponse<MyReviewDetailVO> myReviewDetail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long reviewId
    ) {
        MyReviewDetailVO detail = reviewService.getMyReviewDetail(userId, reviewId);
        return ApiResponse.success(detail);
    }

    // ==================== 评论编辑与删除 结束 ====================

    // ==================== 追评（追加评价）EPIC-08 故事2 ====================

    /**
     * 提交追评（追加评价）。
     *
     * 业务规则：
     * - 只能追评自己发表的、已发布的原评价
     * - 每条原评价最多追加一条追评
     * - 追评正文 10-2000 字符，消费日期必填，评分选填
     * - 不涉及图片上传
     *
     * 请求示例：
     *   POST /api/reviews/1/follow-up
     *   Body: { "content": "第二次来...", "rating": 5, "consumptionDate": "2026-07-18" }
     */
    @PostMapping("/{reviewId}/follow-up")
    public ApiResponse<ReviewSubmitResponse> submitFollowUp(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReviewFollowUpRequest request
    ) {
        ReviewSubmitResponse response = reviewService.submitFollowUpReview(
                userId, reviewId, request
        );
        return ApiResponse.success(response);
    }

    /**
     * 编辑追评。
     *
     * 规则：
     * - 只能编辑自己写的追评
     * - 编辑保留历史版本
     * - 编辑后重新执行内容安全检测
     *
     * 请求示例：
     *   PUT /api/reviews/30/follow-up
     *   Body: { "content": "第三次来，体验更好了...", "rating": 5, "consumptionDate": "2026-07-20" }
     */
    @PutMapping("/{reviewId}/follow-up")
    public ApiResponse<ReviewSubmitResponse> editFollowUp(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReviewFollowUpRequest request
    ) {
        ReviewSubmitResponse response = reviewService.editFollowUpReview(
                userId, reviewId, request
        );
        return ApiResponse.success(response);
    }

    /**
     * 删除追评 —— 逻辑删除。
     *
     * 根据 Jira EPIC-08 故事2 验收准则5：
     * "追评单独删除后原评价仍正常展示"
     *
     * 请求示例：
     *   DELETE /api/reviews/30/follow-up
     */
    @DeleteMapping("/{reviewId}/follow-up")
    public ApiResponse<Void> deleteFollowUp(
            @PathVariable Long reviewId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        reviewService.deleteFollowUpReview(userId, reviewId);
        return ApiResponse.success("追评已删除", null);
    }

    /**
     * 查询某条原评价的追评详情。
     *
     * 如果原评价存在有效追评，返回追评数据；否则返回 null。
     * 前端可通过此接口判断是否展示"追加评价"入口和追评内容。
     *
     * 请求示例：
     *   GET /api/reviews/1/follow-up
     */
    @GetMapping("/{reviewId}/follow-up")
    public ApiResponse<ReviewFollowUpVO> getFollowUp(
            @PathVariable Long reviewId
    ) {
        ReviewFollowUpVO followUp = reviewService.getFollowUpByParentId(reviewId);
        return ApiResponse.success(followUp);
    }

    // ==================== 追评（追加评价）结束 ====================

    @PostMapping("/drop-constraint")
    public ApiResponse<Map<String, Object>> dropConstraint() {
        try {
            Map<String, Object> result = new HashMap<>();

            List<Map<String, Object>> allConstraints = jdbcTemplate.queryForList(
                "SELECT conname, contype FROM pg_constraint WHERE conrelid = 'reviews'::regclass ORDER BY conname"
            );
            result.put("all_constraints_count", allConstraints.size());
            List<String> constraintNames = new ArrayList<>();
            for (Map<String, Object> c : allConstraints) {
                constraintNames.add((String) c.get("conname"));
            }
            result.put("all_constraint_names", constraintNames);

            for (Map<String, Object> constraint : allConstraints) {
                String name = (String) constraint.get("conname");
                if (!name.endsWith("_pkey")) {
                    jdbcTemplate.execute("ALTER TABLE reviews DROP CONSTRAINT IF EXISTS " + name);
                    result.put(name, "dropped");
                }
            }

            List<Map<String, Object>> remaining = jdbcTemplate.queryForList(
                "SELECT conname, contype FROM pg_constraint WHERE conrelid = 'reviews'::regclass"
            );
            result.put("remaining_count", remaining.size());
            List<String> remainingNames = new ArrayList<>();
            for (Map<String, Object> c : remaining) {
                remainingNames.add((String) c.get("conname"));
            }
            result.put("remaining_names", remainingNames);

            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.failure("DROP_FAILED", e.getMessage());
        }
    }

    @PostMapping("/reload-seed")
    public ApiResponse<Map<String, Object>> reloadSeedData() {
        try {
            jdbcTemplate.execute("ALTER TABLE reviews ADD COLUMN IF NOT EXISTS review_time TIMESTAMPTZ");

            jdbcTemplate.execute("DROP INDEX IF EXISTS uk_reviews_user_merchant_original");

            jdbcTemplate.execute("DELETE FROM reviews");

            String[] sqls = {
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (1, 1, 3, 5.0, '味道非常正宗！麻婆豆腐特别好吃，麻辣鲜香，每次来都要点。水煮鱼的分量也很足，两个人吃完全够。', 'SYSTEM', '2026-07-01 12:30:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (2, 1, 3, 3.5, '环境不错，装修挺有格调的，服务态度也很好。但是周末人太多了，排了将近一个小时才吃上，建议工作日来。', 'SYSTEM', '2026-07-03 19:15:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (3, 1, 2, 5.0, '价格实惠分量足，四个朋友一起聚餐人均才七十多。回锅肉做得特别地道，是朋友聚会的好地方！', 'SYSTEM', '2026-07-05 13:00:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (4, 1, 3, 2.0, '上菜速度太慢了！等了半个多小时才上来第一个菜，而且服务员态度冷漠，叫了好几次都没人理。味道再好也不想再来了。', 'SYSTEM', '2026-07-07 20:45:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (5, 1, 2, 4.5, '水煮鱼做得很地道，麻辣鲜香！夫妻肺片也很开胃。就是店面小了点，人多的时候略显拥挤。', 'SYSTEM', '2026-07-09 18:30:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (6, 1, 3, 1.5, '今天吃的麻婆豆腐太咸了，感觉盐放多了，跟之前来的时候完全不是一个水准。而且价格好像涨了，性价比不如以前。', 'SYSTEM', '2026-07-11 12:00:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (7, 2, 3, 5.0, '早茶品种很丰富，虾饺皇和肠粉都很好吃！虾饺皮薄馅大，虾仁很新鲜。环境也很优雅，适合带家人来。', 'SYSTEM', '2026-07-02 09:00:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (8, 2, 3, 4.0, '环境确实优雅，包间装修很有档次，适合商务宴请。白切鸡做得很嫩，就是人均120确实有点贵，性价比一般。', 'SYSTEM', '2026-07-04 19:30:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (9, 2, 2, 5.0, '白切鸡做得非常嫩，蘸料也很正宗！蜜汁叉烧外甜里嫩，小朋友特别喜欢吃。服务人员很专业，换盘倒茶都很及时。', 'SYSTEM', '2026-07-06 12:15:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (13, 3, 3, 4.5, '深夜觅食的好地方！羊肉串烤得外焦里嫩，配上一瓶冰啤酒简直完美。凌晨一点多还能吃到热乎的烧烤，太幸福了。', 'SYSTEM', '2026-07-01 23:30:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (14, 3, 3, 5.0, '烤茄子必点！蒜蓉酱料特别香，茄子烤得软烂入味。价格也很实惠，三个人吃了一百多块就吃撑了，性价比超高。', 'SYSTEM', '2026-07-02 22:00:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (15, 3, 2, 2.5, '味道还行，但是环境真的比较一般。地面有点油腻，桌椅也不太干净，对卫生有要求的人可能会介意。', 'SYSTEM', '2026-07-05 21:15:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (19, 4, 3, 5.0, '牛油果鸡肉沙拉超好吃！鸡胸肉一点都不柴，应该是低温慢煮的，很嫩。沙拉酱汁是店家自制的，酸甜适中。', 'SYSTEM', '2026-07-03 12:30:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (20, 4, 3, 4.5, '环境很清新舒适，适合一个人安静地吃顿饭。冷榨果汁是现做的，很新鲜。就是价格略贵，一份沙拉加果汁要六七十。', 'SYSTEM', '2026-07-05 13:45:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (25, 5, 3, 5.0, '三文鱼刺身厚切真的太满足了！非常新鲜，入口即化。环境也很有日式风情，榻榻米座位很舒服，适合约会。', 'SYSTEM', '2026-07-02 19:00:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (26, 5, 3, 5.0, '烤鳗鱼是招牌中的招牌！外焦里嫩，酱汁浓郁甜香，配米饭简直绝了。服务也很贴心，服务员都是蹲下来点单的，很有日式服务的感觉。', 'SYSTEM', '2026-07-04 20:30:00+08:00', 'PUBLISHED', 'APPROVED')",
                "INSERT INTO reviews (id, merchant_id, user_id, rating, content, source, review_time, status, moderation_status) VALUES (27, 5, 2, 4.0, '环境和氛围很不错，安静适合聊天。刺身拼盘种类丰富，就是价格不便宜，两个人吃了四百多。偶尔犒劳一下自己还行。', 'SYSTEM', '2026-07-06 19:45:00+08:00', 'PUBLISHED', 'APPROVED')"
            };

            for (String sql : sqls) {
                jdbcTemplate.execute(sql);
            }

            jdbcTemplate.execute("SELECT setval('reviews_id_seq', (SELECT COALESCE(MAX(id), 1) FROM reviews))");

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS review_reply (
                    id BIGSERIAL PRIMARY KEY,
                    review_id BIGINT NOT NULL,
                    merchant_id BIGINT NOT NULL,
                    reply_content TEXT NOT NULL,
                    reply_time TIMESTAMPTZ NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_review_reply_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
                    CONSTRAINT fk_review_reply_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE,
                    CONSTRAINT ck_review_reply_status CHECK (status IN ('VISIBLE', 'HIDDEN'))
                )
                """);

            jdbcTemplate.execute("DELETE FROM review_reply");

            String[] replySqls = {
                "INSERT INTO review_reply (id, review_id, merchant_id, reply_content, reply_time, status) VALUES (1, 1, 1, '非常感谢您的认可！麻婆豆腐是本店招牌，我们会持续把控麻辣口感，期待您再次光临~', '2026-07-01 14:20:00+08:00', 'VISIBLE')",
                "INSERT INTO review_reply (id, review_id, merchant_id, reply_content, reply_time, status) VALUES (2, 4, 1, '非常抱歉给您带来不好的用餐体验！我们已经针对上菜慢、服务问题全员培训，欢迎您下次到店监督我们的改进。', '2026-07-07 21:30:00+08:00', 'VISIBLE')",
                "INSERT INTO review_reply (id, review_id, merchant_id, reply_content, reply_time, status) VALUES (3, 7, 2, '感谢好评！我们每日新鲜采购虾料，保证虾饺口感，欢迎周末带家人来喝早茶~', '2026-07-02 10:15:00+08:00', 'VISIBLE')",
                "INSERT INTO review_reply (id, review_id, merchant_id, reply_content, reply_time, status) VALUES (4, 14, 3, '谢谢支持！蒜蓉烤茄子是深夜必点，我们每晚现捣蒜蓉，保证蒜香浓郁，宵夜随时等您！', '2026-07-02 23:50:00+08:00', 'VISIBLE')",
                "INSERT INTO review_reply (id, review_id, merchant_id, reply_content, reply_time, status) VALUES (5, 19, 4, '很高兴您喜欢我们的鸡胸沙拉！鸡胸全部低温慢煮无油，减脂人群专属搭配，欢迎常来~', '2026-07-03 13:00:00+08:00', 'VISIBLE')",
                "INSERT INTO review_reply (id, review_id, merchant_id, reply_content, reply_time, status) VALUES (6, 26, 5, '烤鳗鱼是每日现蒲烧，酱汁独家调配，感谢喜爱！纪念日欢迎提前预约，我们免费布置桌面。', '2026-07-04 21:00:00+08:00', 'VISIBLE')"
            };

            for (String sql : replySqls) {
                jdbcTemplate.execute(sql);
            }

            jdbcTemplate.execute("SELECT setval('review_reply_id_seq', (SELECT COALESCE(MAX(id), 1) FROM review_reply))");

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS notifications (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    review_id BIGINT NOT NULL,
                    merchant_id BIGINT NOT NULL,
                    type VARCHAR(20) NOT NULL DEFAULT 'REVIEW_REPLY',
                    title VARCHAR(200) NOT NULL,
                    review_summary TEXT,
                    reply_summary TEXT,
                    merchant_name VARCHAR(200),
                    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
                    notified BOOLEAN NOT NULL DEFAULT false,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_notifications_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
                    CONSTRAINT fk_notifications_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE,
                    CONSTRAINT ck_notifications_type CHECK (type IN ('REVIEW_REPLY')),
                    CONSTRAINT ck_notifications_status CHECK (status IN ('UNREAD', 'READ'))
                )
                """);

            jdbcTemplate.execute("DELETE FROM notifications");

            jdbcTemplate.execute("SELECT setval('notifications_id_seq', (SELECT COALESCE(MAX(id), 1) FROM notifications))");

            Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reviews", Long.class);
            Long published = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reviews WHERE status = 'PUBLISHED'", Long.class);
            Long replyCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM review_reply", Long.class);

            return ApiResponse.success(Map.of("total", total, "published", published, "replyCount", replyCount));
        } catch (Exception e) {
            return ApiResponse.failure("RELOAD_FAILED", e.getMessage());
        }
    }
    // ==================== 差评归因分析（EPIC-02 Story 4） ====================

    /**
     * 获取商家差评归因统计。
     *
     * 返回指定时间范围内各类问题（卫生、服务、上菜速度等）的数量和占比。
     * 前端用这个数据渲染饼图/柱状图。
     *
     * 请求示例：
     *   GET /api/reviews/merchants/1/issue-stats?startDate=2026-06-01&endDate=2026-07-17
     */
    @GetMapping("/merchants/{merchantId}/issue-stats")
    public ApiResponse<List<IssueStatVO>> getMerchantIssueStats(
            @PathVariable Long merchantId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        List<IssueStatVO> stats = reviewService.getMerchantIssueStats(
                merchantId, startDate, endDate);
        return ApiResponse.success(stats);
    }

    /**
     * 获取某问题类别下的关联评价列表。
     *
     * 商家用户点击某个问题类别（如"上菜速度"）后，调用此接口查看
     * 被归因为该问题的原始评价及 AI 提取的依据片段。
     *
     * 请求示例：
     *   GET /api/reviews/merchants/1/issue-categories/SERVING_SPEED/reviews?pageNum=1&pageSize=10
     */
    @GetMapping("/merchants/{merchantId}/issue-categories/{categoryCode}/reviews")
    public ApiResponse<PageResult<IssueReviewVO>> getIssueCategoryReviews(
            @PathVariable Long merchantId,
            @PathVariable String categoryCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        Page<IssueReviewVO> page = reviewService.getIssueCategoryReviews(
                merchantId, categoryCode, pageNum, pageSize,
                startDate, endDate);
        return ApiResponse.success(PageResult.from(page));
    }

    /**
     * 获取全部可用的问题类别字典。
     *
     * 前端用这个接口渲染类别筛选列表（保证和后端内置类别一致）。
     *
     * 请求示例：
     *   GET /api/reviews/issue-categories
     */
    @GetMapping("/issue-categories")
    public ApiResponse<List<ReviewIssueCategory>> getIssueCategories() {
        List<ReviewIssueCategory> categories = issueCategoryMapper.selectList(
                new LambdaQueryWrapper<ReviewIssueCategory>()
                        .eq(ReviewIssueCategory::getStatus, "ACTIVE")
                        .orderByAsc(ReviewIssueCategory::getId)
        );
        return ApiResponse.success(categories);
    }

    // ==================== 差评归因分析 结束 ====================

}
