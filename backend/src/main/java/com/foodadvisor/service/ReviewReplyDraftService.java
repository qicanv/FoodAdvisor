package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodadvisor.dto.review.ReviewReplyDraftVO;
import com.foodadvisor.dto.review.ReviewReplyVO;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewReply;
import com.foodadvisor.entity.ReviewReplyDraft;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.trace.AiTraceContext;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.ReviewReplyDraftMapper;
import com.foodadvisor.mapper.ReviewReplyMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评价辅助回复草稿服务（EPIC-02 故事7：评价辅助回复）
 *
 * 核心职责：
 * 1. 调用 AI 服务生成回复建议草稿
 * 2. 管理草稿的编辑、发布和丢弃
 * 3. 发布时将草稿转为正式的商家回复（写入 review_reply 表）
 *
 * 业务规则：
 * - 好评和差评使用不同的 AI 回复策略
 * - 生成内容必须由商家确认后才能发布，系统不自动发布
 * - 商家可以在发布前编辑 AI 生成的内容
 * - 每条评价在同一时间最多只有一个活跃草稿（DRAFT 状态）
 * - 模型调用失败时给出明确提示，不覆盖已有草稿
 */
@Service
public class ReviewReplyDraftService extends ServiceImpl<ReviewReplyDraftMapper, ReviewReplyDraft> {

    private final ReviewMapper reviewMapper;
    private final MerchantMapper merchantMapper;
    private final ReviewReplyMapper replyMapper;
    private final AIClientService aiClientService;
    private final NotificationService notificationService;
    private final AiRequestTraceService traceService;

    public ReviewReplyDraftService(
            ReviewMapper reviewMapper,
            MerchantMapper merchantMapper,
            ReviewReplyMapper replyMapper,
            AIClientService aiClientService,
            NotificationService notificationService
    ) {
        this(reviewMapper, merchantMapper, replyMapper, aiClientService, notificationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ReviewReplyDraftService(
            ReviewMapper reviewMapper,
            MerchantMapper merchantMapper,
            ReviewReplyMapper replyMapper,
            AIClientService aiClientService,
            NotificationService notificationService,
            AiRequestTraceService traceService
    ) {
        this.reviewMapper = reviewMapper;
        this.merchantMapper = merchantMapper;
        this.replyMapper = replyMapper;
        this.aiClientService = aiClientService;
        this.notificationService = notificationService;
        this.traceService = traceService;
    }

    // ============================================================
    // 1. 生成回复建议
    // ============================================================

    /**
     * 为指定评价生成 AI 回复建议草稿。
     *
     * 流程：
     * 1. 校验评价存在且属于该商家
     * 2. 检查是否已有活跃草稿（如有则直接返回已有草稿，不重复调用 AI）
     * 3. 调用 AI 服务生成回复
     * 4. 保存草稿到数据库
     *
     * @param merchantMemberId 商家用户 ID（用于权限校验和后续确认记录）
     * @param reviewId         评价 ID
     * @return 生成的草稿 VO
     */
    @Transactional
    public ReviewReplyDraftVO generateDraft(Long merchantMemberId, Long reviewId) {
        AiTraceContext context = traceService == null ? null
                : traceService.startTrace(null, null, merchantMemberId, "REVIEW_REPLY_GENERATION");
        try {
            return generateDraft(merchantMemberId, reviewId, context);
        } catch (RuntimeException exception) {
            failTrace(context, "REPLY_GENERATION_FAILED", exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public ReviewReplyDraftVO generateDraft(Long merchantMemberId, Long reviewId,
                                            AiTraceContext context) {
        recordRequest(context, reviewId, merchantMemberId);
        // --- 1. 校验评价存在 ---
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REVIEW_NOT_FOUND",
                    "评价不存在"
            );
        }

        // --- 2. 校验评价属于该商家 ---
        Long merchantId = review.getMerchantId();
        completeStage(startStage(context, "REVIEW_SOURCE_LOAD", Map.of("reviewId", reviewId,
                        "merchantId", merchantId)),
                Map.of("reviewId", reviewId, "merchantId", merchantId), null, null, null, null);
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "MERCHANT_NOT_FOUND",
                    "商家不存在"
            );
        }

        // --- 3. 检查是否已有活跃草稿（避免重复调用 AI） ---
        ReviewReplyDraft existingDraft = findActiveDraft(reviewId);
        if (existingDraft != null) {
            // 已有草稿，直接返回，不重复生成（节省 AI 调用费用）
            return ReviewReplyDraftVO.from(existingDraft);
        }

        // --- 4. 根据评价情感确定回复策略 ---
        // 策略判断逻辑：
        // - 如果 review_analysis 表中有 sentiment 字段，优先使用
        // - 如果评分 >= 4，视为好评（POSITIVE）
        // - 如果评分 <= 2，视为差评（NEGATIVE）
        // - 评分 == 3，视为中性，默认使用好评策略
        String strategy = determineStrategy(review);
        String promptVersion = "POSITIVE".equals(strategy)
                ? "review-reply-positive:v1" : "review-reply-negative:v1";
        completeStage(startStage(context, "PROMPT_BUILD", Map.of("reviewId", reviewId,
                        "replyType", strategy, "promptVersion", promptVersion)),
                Map.of("replyType", strategy, "promptVersion", promptVersion), null, null, null, promptVersion);

        // --- 5. 调用 AI 服务生成回复 ---
        // 注意：如果 AI 调用失败，异常会向上抛出，由全局异常处理器返回错误提示。
        // generateReplyDraft 方法不会修改任何数据库状态，所以失败时不会有脏数据。
        com.fasterxml.jackson.databind.JsonNode aiResult;
        try {
            aiResult = traceService == null || context == null
                    ? aiClientService.generateReplyDraft(review.getId(), review.getMerchantId(),
                    review.getContent(), strategy,
                    review.getRating() != null ? review.getRating().intValue() : null)
                    : aiClientService.generateReplyDraft(review.getId(), review.getMerchantId(),
                    review.getContent(), strategy,
                    review.getRating() != null ? review.getRating().intValue() : null, context);
        } catch (Exception e) {
            // 根据验收准则7："模型调用失败时显示明确提示且不覆盖已有草稿"
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_REPLY_GENERATION_FAILED",
                    "AI 回复生成失败：" + e.getMessage()
            );
        }

        completeStage(startStage(context, "OUTPUT_VALIDATION", Map.of("reviewId", reviewId)),
                Map.of("status", "SUCCESS"), "FASTAPI", text(aiResult, "modelName"),
                text(aiResult, "modelVersion"), promptVersion);

        // --- 6. 构建并保存草稿 ---
        ReviewReplyDraft draft = new ReviewReplyDraft();
        draft.setReviewId(reviewId);
        draft.setMerchantId(merchantId);
        draft.setGeneratedContent(aiResult.get("replyContent").asText());
        draft.setStrategy(strategy);
        draft.setStatus("DRAFT");
        draft.setGeneratedAt(OffsetDateTime.now());
        draft.setAiTraceId(context == null
                ? (aiResult.has("businessTraceId") && !aiResult.get("businessTraceId").isNull()
                ? aiResult.get("businessTraceId").asText() : null)
                : context.traceId());
        draft.setModelName(
                aiResult.has("modelName") && !aiResult.get("modelName").isNull()
                        ? aiResult.get("modelName").asText()
                        : null
        );

        AiRequestTraceStage persistStage = startStage(context, "RESULT_PERSIST",
                Map.of("reviewId", reviewId, "merchantId", merchantId));
        if (!this.save(draft)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DRAFT_SAVE_FAILED",
                    "草稿保存失败"
            );
        }

        ReviewReplyDraftVO response = ReviewReplyDraftVO.from(draft);
        response.setTraceId(context == null ? response.getAiTraceId() : context.traceId());
        Map<String, Object> persistSummary = new LinkedHashMap<>();
        persistSummary.put("replyDraftId", draft.getId());
        persistSummary.put("status", "DRAFT");
        completeStage(persistStage, persistSummary,
                null, draft.getModelName(), null, promptVersion);
        finishSuccess(context, response, reviewId, merchantId, promptVersion);
        return response;
    }

    // ============================================================
    // 2. 编辑草稿
    // ============================================================

    /**
     * 编辑 AI 生成草稿的内容。
     *
     * 商家用户可以对 AI 生成的回复进行修改，使其更符合实际情况。
     * 修改后的内容保存到 editedContent 字段，发布时优先使用编辑后的内容。
     *
     * 根据验收准则3："商家能够在提交前编辑生成内容"
     *
     * @param merchantMemberId 商家用户 ID
     * @param reviewId         评价 ID
     * @param editedContent    编辑后的回复内容
     * @return 更新后的草稿 VO
     */
    @Transactional
    private void recordRequest(AiTraceContext context, Long reviewId, Long merchantMemberId) {
        if (context == null || traceService == null) return;
        traceService.updateStructuredConditions(context, Map.of("reviewId", reviewId));
        completeStage(startStage(context, "REQUEST_RECEIVED", Map.of("reviewId", reviewId,
                        "merchantMemberId", merchantMemberId)), Map.of("accepted", true),
                null, null, null, null);
    }

    private AiRequestTraceStage startStage(AiTraceContext context, String name, Object input) {
        if (context == null || traceService == null) return null;
        try { return traceService.startStage(context, name, input); } catch (Exception ignored) { return null; }
    }

    private void completeStage(AiRequestTraceStage stage, Object output, String provider,
                               String modelName, String modelVersion, String promptVersion) {
        if (stage == null || traceService == null) return;
        try { traceService.completeStage(stage, output, provider, modelName, modelVersion, promptVersion); }
        catch (Exception ignored) { }
    }

    private void finishSuccess(AiTraceContext context, ReviewReplyDraftVO response,
                               Long reviewId, Long merchantId, String promptVersion) {
        if (context == null || traceService == null) return;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("replyDraftId", response.getId());
        summary.put("reviewId", reviewId);
        summary.put("merchantId", merchantId);
        summary.put("status", response.getStatus());
        summary.put("degraded", false);
        summary.put("replyType", response.getStrategy());
        traceService.completeTrace(context, "SUCCESS", summary, "FASTAPI",
                response.getModelName(), null, promptVersion);
    }

    private void failTrace(AiTraceContext context, String code, String message) {
        if (context != null && traceService != null) traceService.failTraceSafely(context, code, message);
    }

    private String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText() : null;
    }

    public ReviewReplyDraftVO editDraft(Long merchantMemberId, Long reviewId, String editedContent) {
        // --- 1. 校验评价存在 ---
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REVIEW_NOT_FOUND",
                    "评价不存在"
            );
        }

        // --- 2. 校验草稿存在且属于该商家 ---
        ReviewReplyDraft draft = requireActiveDraft(reviewId, review.getMerchantId());

        // --- 3. 校验编辑内容不为空 ---
        if (editedContent == null || editedContent.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DRAFT_CONTENT_EMPTY",
                    "回复内容不能为空"
            );
        }

        // --- 4. 更新草稿内容 ---
        // 安全校验：不使用攻击性语言（验收准则5要求）
        String sanitized = sanitizeContent(editedContent);
        draft.setEditedContent(sanitized);
        draft.setUpdatedAt(OffsetDateTime.now());

        if (!this.updateById(draft)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DRAFT_UPDATE_FAILED",
                    "草稿更新失败"
            );
        }

        return ReviewReplyDraftVO.from(draft);
    }

    // ============================================================
    // 3. 发布草稿（转为正式回复）
    // ============================================================

    /**
     * 将 AI 生成的草稿发布为正式商家回复。
     *
     * 流程：
     * 1. 校验草稿存在且状态为 DRAFT
     * 2. 获取最终回复内容（编辑后的 > AI 原始生成的）
     * 3. 写入 review_reply 表（如果已有回复则更新）
     * 4. 更新草稿状态为 PUBLISHED
     *
     * 根据验收准则4："未点击确认时回复不会进入已发布状态"
     *
     * @param merchantMemberId 商家用户 ID
     * @param reviewId         评价 ID
     * @return 发布后的正式回复 VO
     */
    @Transactional
    public ReviewReplyVO publishDraft(Long merchantMemberId, Long reviewId) {
        // --- 1. 校验评价存在 ---
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REVIEW_NOT_FOUND",
                    "评价不存在"
            );
        }

        Long merchantId = review.getMerchantId();

        // --- 2. 校验草稿存在 ---
        ReviewReplyDraft draft = requireActiveDraft(reviewId, merchantId);

        // --- 3. 获取最终回复内容 ---
        // 优先使用商家编辑后的内容，否则使用 AI 生成的原始内容
        String finalContent = draft.getEditedContent() != null && !draft.getEditedContent().isBlank()
                ? draft.getEditedContent()
                : draft.getGeneratedContent();

        // --- 4. 安全检查：最终内容再次过滤 ---
        finalContent = sanitizeContent(finalContent);

        // --- 5. 写入或更新 review_reply 表 ---
        // 如果该评价已有商家回复，则更新内容；否则新增
        ReviewReply existingReply = replyMapper.selectOne(
                new LambdaQueryWrapper<ReviewReply>()
                        .eq(ReviewReply::getReviewId, reviewId)
        );

        ReviewReply reply;
        OffsetDateTime now = OffsetDateTime.now();

        if (existingReply != null) {
            // 更新已有回复
            reply = existingReply;
            reply.setReplyContent(finalContent);
            reply.setReplyTime(now);
            reply.setStatus("VISIBLE");
            reply.setUpdatedAt(now);
            replyMapper.updateById(reply);
        } else {
            // 新增回复
            reply = new ReviewReply();
            reply.setReviewId(reviewId);
            reply.setMerchantId(merchantId);
            reply.setReplyContent(finalContent);
            reply.setReplyTime(now);
            reply.setStatus("VISIBLE");
            reply.setCreatedAt(now);
            reply.setUpdatedAt(now);
            replyMapper.insert(reply);
        }

        // --- 6. 发送回复通知给评价作者 ---
        Merchant merchant = merchantMapper.selectById(merchantId);
        notificationService.createReplyNotification(
                reviewId, merchantId, finalContent,
                merchant != null ? merchant.getName() : null
        );

        // --- 7. 更新草稿状态为 PUBLISHED ---
        draft.setStatus("PUBLISHED");
        draft.setPublishedAt(now);
        draft.setConfirmedBy(merchantMemberId);
        draft.setUpdatedAt(now);

        if (!this.updateById(draft)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DRAFT_PUBLISH_FAILED",
                    "草稿发布状态更新失败"
            );
        }

        // --- 8. 构建返回 VO ---
        ReviewReplyVO replyVO = new ReviewReplyVO();
        replyVO.setId(reply.getId());
        replyVO.setReviewId(reply.getReviewId());
        replyVO.setMerchantId(reply.getMerchantId());
        replyVO.setReplyContent(reply.getReplyContent());
        replyVO.setReplyTime(reply.getReplyTime());
        replyVO.setStatus(reply.getStatus());
        replyVO.setMerchantName(merchant != null ? merchant.getName() : null);

        return replyVO;
    }

    // ============================================================
    // 4. 查询草稿
    // ============================================================

    /**
     * 获取某条评价的当前草稿（如果有）。
     *
     * 前端可以在商家查看评价时调用，判断是否需要展示已有的草稿。
     *
     * @param reviewId 评价 ID
     * @return 草稿 VO，如果没有活跃草稿则返回 null
     */
    public ReviewReplyDraftVO getDraft(Long reviewId) {
        ReviewReplyDraft draft = findActiveDraft(reviewId);
        return ReviewReplyDraftVO.from(draft);
    }

    // ============================================================
    // 5. 丢弃草稿
    // ============================================================

    /**
     * 丢弃 AI 生成的草稿（商家选择不使用该建议）。
     *
     * 丢弃后的草稿状态变为 DISCARDED，不再出现在查询中。
     * 商家之后可以重新请求生成新的回复建议。
     *
     * @param merchantMemberId 商家用户 ID
     * @param reviewId         评价 ID
     */
    @Transactional
    public void discardDraft(Long merchantMemberId, Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REVIEW_NOT_FOUND",
                    "评价不存在"
            );
        }

        ReviewReplyDraft draft = requireActiveDraft(reviewId, review.getMerchantId());

        draft.setStatus("DISCARDED");
        draft.setUpdatedAt(OffsetDateTime.now());

        if (!this.updateById(draft)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DRAFT_DISCARD_FAILED",
                    "草稿丢弃失败"
            );
        }
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 查找指定评价的活跃草稿（状态为 DRAFT）。
     *
     * 每条评价同一时间最多只有一个活跃草稿。
     *
     * @param reviewId 评价 ID
     * @return 活跃草稿，如果没有则返回 null
     */
    private ReviewReplyDraft findActiveDraft(Long reviewId) {
        return this.getOne(
                new LambdaQueryWrapper<ReviewReplyDraft>()
                        .eq(ReviewReplyDraft::getReviewId, reviewId)
                        .eq(ReviewReplyDraft::getStatus, "DRAFT")
                        .orderByDesc(ReviewReplyDraft::getGeneratedAt)
                        .last("LIMIT 1")
        );
    }

    /**
     * 查找并校验活跃草稿存在且属于指定商家。
     *
     * @param reviewId   评价 ID
     * @param merchantId 商家 ID（用于权限校验）
     * @return 活跃草稿
     * @throws ApiException 如果草稿不存在或不属于该商家
     */
    private ReviewReplyDraft requireActiveDraft(Long reviewId, Long merchantId) {
        ReviewReplyDraft draft = findActiveDraft(reviewId);
        if (draft == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "DRAFT_NOT_FOUND",
                    "没有找到活跃的回复草稿，请先生成回复建议"
            );
        }

        // 权限校验：确保草稿属于该商家
        if (!draft.getMerchantId().equals(merchantId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "只能操作自己店铺的评价回复草稿"
            );
        }

        return draft;
    }

    /**
     * 根据评价评分确定 AI 回复策略。
     *
     * 策略规则：
     * - 评分 >= 4 → POSITIVE（好评策略：感谢 + 回应具体优点）
     * - 评分 <= 2 → NEGATIVE（差评策略：道歉 + 问题说明 + 改进承诺）
     * - 评分 == 3 → POSITIVE（中性评价默认使用好评策略，偏温和感谢）
     * - 如果评分不存在 → POSITIVE（默认保守策略）
     *
     * 根据验收准则1："好评和差评分别使用预先定义的不同回复策略"
     *
     * @param review 评价实体
     * @return 策略标识：POSITIVE 或 NEGATIVE
     */
    private String determineStrategy(Review review) {
        if (review.getRating() == null) {
            return "POSITIVE";
        }

        int rating = review.getRating().intValue();
        if (rating >= 4) {
            return "POSITIVE";
        } else if (rating <= 2) {
            return "NEGATIVE";
        } else {
            // rating == 3：中性评价，默认使用好评策略
            return "POSITIVE";
        }
    }

    /**
     * 对回复内容进行基础的安全清洗。
     *
     * 根据验收准则5："使用安全测试输入时，生成回复不包含辱骂、攻击、
     * 完整联系方式或敏感隐私"
     *
     * 这里做一层基础的规则过滤，核心安全检测由 AI 服务的 prompt 层面保证。
     *
     * @param content 原始内容
     * @return 清洗后的内容
     */
    private String sanitizeContent(String content) {
        if (content == null) {
            return null;
        }

        // 移除完整的手机号格式（中国大陆手机号）
        content = content.replaceAll("1[3-9]\\d{9}", "[已隐藏联系方式]");

        // 移除完整的邮箱格式
        content = content.replaceAll(
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
                "[已隐藏邮箱]"
        );

        return content.trim();
    }
}
