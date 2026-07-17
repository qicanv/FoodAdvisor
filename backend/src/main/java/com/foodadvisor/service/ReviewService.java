package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodadvisor.backend.exception.ApiException;
import com.foodadvisor.dto.review.MerchantRatingSummaryVO;
import com.foodadvisor.dto.review.ReviewImageVO;
import com.foodadvisor.dto.review.ReviewSubmitRequest;
import com.foodadvisor.dto.review.ReviewSubmitResponse;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewAnalysis;
import com.foodadvisor.entity.ReviewImage;
import com.foodadvisor.entity.ReviewTagRelation;
import com.foodadvisor.entity.ReviewVersion;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.ReviewAnalysisMapper;
import com.foodadvisor.mapper.ReviewImageMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.ReviewTagRelationMapper;
import com.foodadvisor.mapper.ReviewVersionMapper;
import com.foodadvisor.storage.ReviewImageStorageService;
import com.foodadvisor.storage.StoredReviewImage;
import com.foodadvisor.dto.review.ReviewTagStatVO;
import com.foodadvisor.entity.ReviewTag;
import com.foodadvisor.mapper.ReviewTagMapper;
import com.foodadvisor.mapper.TagRelationWithName;
import com.foodadvisor.mapper.TagSentimentCount;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评价服务
 */
@Service
public class ReviewService extends ServiceImpl<ReviewMapper, Review> {

    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_IMAGE_COUNT = 9;

    private static final Set<String> HIGH_RISK_WORDS = Set.of(
            "暴恐",
            "涉政",
            "色情",
            "赌博",
            "毒品"
    );

    private final ReviewAnalysisMapper analysisMapper;
    private final ReviewTagRelationMapper tagRelationMapper;
    private final ReviewImageMapper imageMapper;
    private final ReviewVersionMapper versionMapper;
    private final MerchantMapper merchantMapper;
    private final ReviewImageStorageService imageStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final ReviewTagMapper tagMapper;
    private final com.foodadvisor.mapper.UserMapper userMapper;

    public ReviewService(
            ReviewAnalysisMapper analysisMapper,
            ReviewTagRelationMapper tagRelationMapper,
            ReviewTagMapper tagMapper,
            ReviewImageMapper imageMapper,
            ReviewVersionMapper versionMapper,
            MerchantMapper merchantMapper,
            ReviewImageStorageService imageStorageService,
            JdbcTemplate jdbcTemplate,
            com.foodadvisor.mapper.UserMapper userMapper
    ) {
        this.analysisMapper = analysisMapper;
        this.tagRelationMapper = tagRelationMapper;
        this.tagMapper = tagMapper;
        this.imageMapper = imageMapper;
        this.versionMapper = versionMapper;
        this.merchantMapper = merchantMapper;
        this.imageStorageService = imageStorageService;
        this.jdbcTemplate = jdbcTemplate;
        this.userMapper = userMapper;
    }

    /**
     * 按商家分页查询公开评价。
     */
    public Page<Review> listByMerchant(
            Long merchantId,
            int pageNum,
            int pageSize
    ) {
        LambdaQueryWrapper<Review> wrapper =
                new LambdaQueryWrapper<>();

        wrapper.eq(Review::getMerchantId, merchantId)
                .eq(Review::getStatus, "PUBLISHED")
                .orderByDesc(Review::getCreatedAt);

        List<Review> records = this.list(wrapper);
        
        Page<Review> page = new Page<>();
        page.setRecords(records);
        page.setTotal((long) records.size());
        page.setCurrent(pageNum);
        page.setSize(pageSize);
        
        return page;
    }

    /**
     * 按商家分页查询公开评价，包含用户信息。
     */
    public Page<com.foodadvisor.dto.review.ReviewDisplayVO> listByMerchantWithUser(
            Long merchantId,
            int pageNum,
            int pageSize
    ) {
        Page<Review> reviewPage = listByMerchant(merchantId, pageNum, pageSize);
        
        List<com.foodadvisor.dto.review.ReviewDisplayVO> displayVOs = new ArrayList<>();
        for (Review review : reviewPage.getRecords()) {
            com.foodadvisor.dto.review.ReviewDisplayVO vo = com.foodadvisor.dto.review.ReviewDisplayVO.from(review);
            
            if (review.getUserId() != null) {
                com.foodadvisor.entity.User user = userMapper.selectById(review.getUserId());
                if (user != null) {
                    vo.setUsername(user.getUsername());
                    vo.setNickname(user.getNickname());
                }
            }
            
            displayVOs.add(vo);
        }
        
        Page<com.foodadvisor.dto.review.ReviewDisplayVO> page = new Page<>();
        page.setRecords(displayVOs);
        page.setTotal(reviewPage.getTotal());
        page.setCurrent(pageNum);
        page.setSize(pageSize);
        
        return page;
    }

    /**
     * 提交或重新提交评价。
     */
    @Transactional
    public ReviewSubmitResponse submitOriginalReview(
            Long userId,
            Long merchantId,
            ReviewSubmitRequest request,
            List<MultipartFile> images
    ) {
        validateSubmitRequest(request);

        Merchant merchant = requireReviewableMerchant(merchantId);

        Review review = findCurrentOriginalReview(
                userId,
                merchantId
        );

        boolean creating = review == null;

        if (creating) {
            review = new Review();
            review.setUserId(userId);
            review.setMerchantId(merchantId);
            review.setReviewType("ORIGINAL");
            review.setSource("SYSTEM");
            review.setCurrentVersion(1);
            review.setCreatedAt(OffsetDateTime.now());
        } else {
            int currentVersion =
                    review.getCurrentVersion() == null
                            ? 1
                            : review.getCurrentVersion();

            review.setCurrentVersion(currentVersion + 1);
            review.setEditedAt(OffsetDateTime.now());
        }

        applyReviewFields(review, request);
        applyContentSafety(review);

        if (creating) {
            this.save(review);
        } else {
            this.updateById(review);
        }

        List<ReviewImage> activeImages = replaceImages(
                review.getId(),
                request.getKeepImageIds(),
                images
        );

        saveVersion(
                review,
                activeImages,
                creating ? "CREATE" : "EDIT"
        );

        refreshMerchantRatingStats(merchant.getId());
        triggerAnalysisPlaceholder(review);

        return toSubmitResponse(review, activeImages);
    }

    // ==================== 评论编辑与删除 ====================

    /**
     * 公共校验：评价必须存在、属于当前用户、且未被逻辑删除。
     * 编辑和删除都需要相同的权限检查，抽出来避免重复代码
     */
    private Review requireOwnedActiveReview(Long userId, Long reviewId) {
        // 查数据库评论是否存在
        Review review = this.getById(reviewId);
        if (review == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REVIEW_NOT_FOUND",
                    "评论不存在"
            );
        }

        // 权限校验：这条评论是否是用户自己写的
        if (!review.getUserId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "REVIEW_NOT_OWNED",
                    "只能操作自己的评论"
            );
        }

        // 状态校验：已删除的评论不允许再操作
        if ("DELETED".equals(review.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REVIEW_DELETED",
                    "已删除的评论不可操作"
            );
        }

        return review;
    }

    /**
     * 编辑评价 —— 仅允许作者自己修改评价。
     * 流程和"重新提交评价"基本一致：版本号+1、内容覆盖、安全审核、
     * 图片增删、版本快照、刷新商家评分、触发AI重新分析。
     */
    @Transactional
    public ReviewSubmitResponse editReview(
            Long userId,
            Long reviewId,
            ReviewSubmitRequest request,
            List<MultipartFile> images
    ) {
        // 入参格式校验（内容长度、评分范围等），复用提交评价的校验逻辑
        validateSubmitRequest(request);

        // 前置校验：存在性 + 归属 + 未删除（公共方法，编辑和删除共用）
        Review review = requireOwnedActiveReview(userId, reviewId);

        // 版本号 +1，记录这是第几次修改
        int currentVersion = review.getCurrentVersion() == null
                ? 1
                : review.getCurrentVersion();
        review.setCurrentVersion(currentVersion + 1);

        // 记录编辑时间（写入数据库 edited_at 列）
        review.setEditedAt(OffsetDateTime.now());

        // 用前端传来的新内容覆盖旧字段（content、rating、tasteRating 等）
        applyReviewFields(review, request);

        // 内容安全审核：如果新内容包含敏感词，状态会变成 PENDING 待审核
        applyContentSafety(review);

        // 执行 UPDATE SQL，把改动持久化到 reviews 表
        this.updateById(review);

        // 处理图片：保留 keepImageIds 中的旧图 + 上传新图，其余标记删除
        List<ReviewImage> activeImages = replaceImages(
                review.getId(),
                request.getKeepImageIds(),
                images
        );

        // 在 review_versions 表存一份快照，changeType = "EDIT"
        saveVersion(review, activeImages, "EDIT");

        // 编辑可能改了评分，重新计算商家的平均分和评价总数
        refreshMerchantRatingStats(review.getMerchantId());

        // 插入一条 PENDING 状态的分析占位记录，等 AI 服务来重新分析
        triggerAnalysisPlaceholder(review);

        // 把最终的 Review 对象 + 图片列表转成前端要的 JSON 格式
        return toSubmitResponse(review, activeImages);
    }

    /**
     * 删除评价 —— 逻辑删除，仅修改 status 和 deleted_at，不物理删除数据。
     * 数据仍然保留在数据库里，方便后续审计和数据分析。
     */
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        // 前置校验：存在性 + 归属 + 未删除
        Review review = requireOwnedActiveReview(userId, reviewId);

        // 版本号 +1：因为 saveVersion 依赖 (review_id, version) 唯一约束，
        // 编辑已经占了当前版本号，删除必须用新版本号才能插入版本快照
        int currentVersion = review.getCurrentVersion() == null
                ? 1
                : review.getCurrentVersion();
        review.setCurrentVersion(currentVersion + 1);

        // 逻辑删除：改状态 + 记录删除时间，数据仍然留在数据库里
        review.setStatus("DELETED");
        review.setDeletedAt(OffsetDateTime.now());
        review.setUpdatedAt(OffsetDateTime.now());

        // 执行 UPDATE
        this.updateById(review);

        // 记录版本快照，changeType = "DELETE"，方便追溯
        // List.of() 表示删除后没有保留任何图片
        saveVersion(review, List.of(), "DELETE");

        // 删掉的评价不再计入商家评分，重新算一次
        refreshMerchantRatingStats(review.getMerchantId());
    }

    /**
     * 分页查询当前用户的评价列表（"我的评价"页面用）。
     */
    public Page<Review> listByUser(
            Long userId,
            int pageNum,
            int pageSize,
            String statusFilter
    ) {
        Page<Review> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();

        // 核心条件：只查当前用户自己的评价
        wrapper.eq(Review::getUserId, userId);

        // 状态筛选逻辑
        if (statusFilter != null && !statusFilter.isBlank()) {
            // 前端明确指定了筛选状态（比如只看已发布的）
            wrapper.eq(Review::getStatus, statusFilter);
        } else {
            // 默认不展示已删除的
            wrapper.ne(Review::getStatus, "DELETED");
        }

        // 按更新时间倒序，最近改动的排最上面
        wrapper.orderByDesc(Review::getUpdatedAt);

        return this.page(page, wrapper);
    }

// ==================== 评论编辑与删除 结束====================

    /**
     * 计算商家评分汇总。
     */
    public MerchantRatingSummaryVO calculateMerchantRatingSummary(
            Long merchantId
    ) {
        LambdaQueryWrapper<Review> wrapper =
                publicReviewWrapper(merchantId);

        List<Review> reviews = this.list(wrapper);

        MerchantRatingSummaryVO summary =
                new MerchantRatingSummaryVO();

        summary.setRatingCount((long) reviews.size());

        summary.setAverageRating(
                average(
                        reviews.stream()
                                .map(Review::getRating)
                                .toList()
                )
        );

        summary.setAverageTasteRating(
                average(
                        reviews.stream()
                                .map(Review::getTasteRating)
                                .toList()
                )
        );

        summary.setAverageEnvironmentRating(
                average(
                        reviews.stream()
                                .map(Review::getEnvironmentRating)
                                .toList()
                )
        );

        summary.setAverageServiceRating(
                average(
                        reviews.stream()
                                .map(Review::getServiceRating)
                                .toList()
                )
        );

        return summary;
    }

    /**
     * 获取评价分析结果。
     */
    public ReviewAnalysis getAnalysis(Long reviewId) {
        LambdaQueryWrapper<ReviewAnalysis> wrapper =
                new LambdaQueryWrapper<>();

        wrapper.eq(
                ReviewAnalysis::getReviewId,
                reviewId
        );

        return analysisMapper.selectOne(wrapper);
    }

    /**
     * 获取评价标签关联。
     */
    public List<ReviewTagRelation> getTagRelations(Long reviewId) {
        LambdaQueryWrapper<ReviewTagRelation> wrapper =
                new LambdaQueryWrapper<>();

        wrapper.eq(
                ReviewTagRelation::getReviewId,
                reviewId
        );

        return tagRelationMapper.selectList(wrapper);
    }

    /**
     * 保存或更新分析结果。
     *
     * 使用原生 SQL 处理 PostgreSQL JSONB 字段。
     */
    @Transactional
    public void saveAnalysis(ReviewAnalysis analysis) {
        LambdaQueryWrapper<ReviewAnalysis> wrapper =
                new LambdaQueryWrapper<>();

        wrapper.eq(
                ReviewAnalysis::getReviewId,
                analysis.getReviewId()
        );

        ReviewAnalysis existing =
                analysisMapper.selectOne(wrapper);

        if (existing != null) {
            jdbcTemplate.update(
                    """
                    UPDATE review_analysis
                    SET review_version = ?,
                        analysis_version = ?,
                        sentiment = ?,
                        confidence = ?,
                        low_confidence = ?,
                        keywords = ?::jsonb,
                        aspects = ?::jsonb,
                        negative_reason = ?,
                        model_name = ?,
                        model_version = ?,
                        business_trace_id = ?,
                        status = ?,
                        error_message = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE review_id = ?
                    """,
                    analysis.getReviewVersion(),
                    analysis.getAnalysisVersion(),
                    analysis.getSentiment(),
                    analysis.getConfidence(),
                    analysis.getLowConfidence(),
                    analysis.getKeywords(),
                    analysis.getAspects(),
                    analysis.getNegativeReason(),
                    analysis.getModelName(),
                    analysis.getModelVersion(),
                    analysis.getBusinessTraceId(),
                    analysis.getStatus(),
                    analysis.getErrorMessage(),
                    analysis.getReviewId()
            );
        } else {
            jdbcTemplate.update(
                    """
                    INSERT INTO review_analysis (
                        review_id,
                        review_version,
                        analysis_version,
                        sentiment,
                        confidence,
                        low_confidence,
                        keywords,
                        aspects,
                        negative_reason,
                        model_name,
                        model_version,
                        business_trace_id,
                        status,
                        error_message,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        ?, ?, ?, ?, ?, ?,
                        ?::jsonb,
                        ?::jsonb,
                        ?, ?, ?, ?, ?, ?,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    )
                    """,
                    analysis.getReviewId(),
                    analysis.getReviewVersion(),
                    analysis.getAnalysisVersion(),
                    analysis.getSentiment(),
                    analysis.getConfidence(),
                    analysis.getLowConfidence(),
                    analysis.getKeywords(),
                    analysis.getAspects(),
                    analysis.getNegativeReason(),
                    analysis.getModelName(),
                    analysis.getModelVersion(),
                    analysis.getBusinessTraceId(),
                    analysis.getStatus(),
                    analysis.getErrorMessage()
            );
        }
    }

    /**
     * 批量保存标签关联。
     */
    @Transactional
    public void saveTagRelations(
            List<ReviewTagRelation> relations
    ) {
        for (ReviewTagRelation relation : relations) {
            LambdaQueryWrapper<ReviewTagRelation> wrapper =
                    new LambdaQueryWrapper<>();

            wrapper.eq(
                            ReviewTagRelation::getReviewId,
                            relation.getReviewId()
                    )
                    .eq(
                            ReviewTagRelation::getTagId,
                            relation.getTagId()
                    );

            tagRelationMapper.delete(wrapper);
            tagRelationMapper.insert(relation);
        }
    }

    /**
     * 统计商家指定情感类型的评价数量。
     */
    public long countBySentiment(
            Long merchantId,
            String sentiment
    ) {
        LambdaQueryWrapper<Review> reviewWrapper =
                new LambdaQueryWrapper<>();

        reviewWrapper.eq(
                        Review::getMerchantId,
                        merchantId
                )
                .eq(
                        Review::getStatus,
                        "PUBLISHED"
                );

        List<Long> reviewIds = this.list(reviewWrapper)
                .stream()
                .map(Review::getId)
                .toList();

        if (reviewIds.isEmpty()) {
            return 0;
        }

        LambdaQueryWrapper<ReviewAnalysis> analysisWrapper =
                new LambdaQueryWrapper<>();

        analysisWrapper.in(
                        ReviewAnalysis::getReviewId,
                        reviewIds
                )
                .eq(
                        ReviewAnalysis::getSentiment,
                        sentiment
                );

        return analysisMapper.selectCount(analysisWrapper);
    }

    private void validateSubmitRequest(
            ReviewSubmitRequest request
    ) {
        if (request == null) {
            throw badRequest(
                    "REVIEW_REQUEST_REQUIRED",
                    "Review request is required"
            );
        }

        String content =
                request.getContent() == null
                        ? ""
                        : request.getContent().trim();

        if (content.length() < MIN_CONTENT_LENGTH) {
            throw badRequest(
                    "REVIEW_CONTENT_TOO_SHORT",
                    "Review content must be at least 10 characters"
            );
        }

        if (content.length() > MAX_CONTENT_LENGTH) {
            throw badRequest(
                    "REVIEW_CONTENT_TOO_LONG",
                    "Review content must be 2000 characters or less"
            );
        }

        if (request.getRating() == null) {
            throw badRequest(
                    "REVIEW_RATING_REQUIRED",
                    "Overall rating is required"
            );
        }

        validateRating(
                "REVIEW_RATING_INVALID",
                request.getRating(),
                "Overall rating"
        );

        validateRating(
                "REVIEW_TASTE_RATING_INVALID",
                request.getTasteRating(),
                "Taste rating"
        );

        validateRating(
                "REVIEW_ENVIRONMENT_RATING_INVALID",
                request.getEnvironmentRating(),
                "Environment rating"
        );

        validateRating(
                "REVIEW_SERVICE_RATING_INVALID",
                request.getServiceRating(),
                "Service rating"
        );

        if (request.getAverageSpend() != null
                && request.getAverageSpend()
                .compareTo(BigDecimal.ZERO) < 0) {
            throw badRequest(
                    "REVIEW_AVERAGE_SPEND_INVALID",
                    "Average spend cannot be negative"
            );
        }
    }

    private void validateRating(
            String code,
            Integer value,
            String fieldName
    ) {
        if (value != null && (value < 1 || value > 5)) {
            throw badRequest(
                    code,
                    fieldName + " must be between 1 and 5"
            );
        }
    }

    private Merchant requireReviewableMerchant(
            Long merchantId
    ) {
        Merchant merchant =
                merchantMapper.selectById(merchantId);

        if (merchant == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "MERCHANT_NOT_FOUND",
                    "Merchant not found"
            );
        }

        boolean active =
                "ACTIVE".equals(
                        merchant.getPlatformStatus()
                );

        boolean operating =
                "OPERATING".equals(
                        merchant.getOperationStatus()
                );

        if (!active
                || !operating
                || merchant.getDeletedAt() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MERCHANT_NOT_REVIEWABLE",
                    "This merchant is disabled or closed and cannot receive new reviews"
            );
        }

        return merchant;
    }

    private Review findCurrentOriginalReview(
            Long userId,
            Long merchantId
    ) {
        return this.getOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .eq(Review::getMerchantId, merchantId)
                        .eq(Review::getReviewType, "ORIGINAL")
                        .ne(Review::getStatus, "DELETED")
                        .last("LIMIT 1")
        );
    }

    private void applyReviewFields(
            Review review,
            ReviewSubmitRequest request
    ) {
        review.setContent(
                request.getContent().trim()
        );

        review.setRating(request.getRating() != null ? BigDecimal.valueOf(request.getRating()) : null);
        review.setTasteRating(request.getTasteRating() != null ? BigDecimal.valueOf(request.getTasteRating()) : null);
        review.setEnvironmentRating(
                request.getEnvironmentRating() != null ? BigDecimal.valueOf(request.getEnvironmentRating()) : null
        );
        review.setServiceRating(
                request.getServiceRating() != null ? BigDecimal.valueOf(request.getServiceRating()) : null
        );
        review.setAverageSpend(
                request.getAverageSpend()
        );
        review.setConsumptionDate(
                request.getConsumptionDate()
        );
        review.setUpdatedAt(OffsetDateTime.now());
    }

    private void applyContentSafety(Review review) {
        boolean highRisk =
                HIGH_RISK_WORDS.stream()
                        .anyMatch(
                                word ->
                                        review.getContent()
                                                .contains(word)
                        );

        if (highRisk) {
            review.setStatus("PENDING");
            review.setModerationStatus("PENDING");
            review.setRiskLevel("HIGH");
            review.setPublishedAt(null);
        } else {
            review.setStatus("PUBLISHED");
            review.setModerationStatus("APPROVED");
            review.setRiskLevel("LOW");

            if (review.getPublishedAt() == null) {
                review.setPublishedAt(
                        OffsetDateTime.now()
                );
            }
        }
    }

    private List<ReviewImage> replaceImages(
            Long reviewId,
            List<Long> keepImageIds,
            List<MultipartFile> newImages
    ) {
        List<ReviewImage> existing =
                imageMapper.selectList(
                        new LambdaQueryWrapper<ReviewImage>()
                                .eq(
                                        ReviewImage::getReviewId,
                                        reviewId
                                )
                                .eq(
                                        ReviewImage::getStatus,
                                        "ACTIVE"
                                )
                                .orderByAsc(
                                        ReviewImage::getSortOrder
                                )
                );

        Set<Long> keepIds =
                keepImageIds == null
                        ? Set.of()
                        : new LinkedHashSet<>(keepImageIds);

        if (!keepIds.isEmpty()) {
            Set<Long> existingIds =
                    existing.stream()
                            .map(ReviewImage::getId)
                            .collect(
                                    java.util.stream.Collectors.toSet()
                            );

            if (!existingIds.containsAll(keepIds)) {
                throw badRequest(
                        "REVIEW_IMAGE_KEEP_INVALID",
                        "Some retained images do not belong to this review"
                );
            }
        }

        Map<Long, ReviewImage> existingById =
                existing.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ReviewImage::getId,
                                        image -> image
                                )
                        );

        List<ReviewImage> kept = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (ReviewImage image : existing) {
            if (keepIds.contains(image.getId())) {
                continue;
            }

            image.setStatus("DELETED");
            image.setDeletedAt(now);
            imageMapper.updateById(image);
        }

        for (Long keepId : keepIds) {
            kept.add(existingById.get(keepId));
        }

        int newImageCount =
                newImages == null
                        ? 0
                        : newImages.size();

        if (kept.size() + newImageCount
                > MAX_IMAGE_COUNT) {
            throw badRequest(
                    "REVIEW_IMAGE_LIMIT_EXCEEDED",
                    "Each review can contain at most 9 images"
            );
        }

        List<ReviewImage> active =
                new ArrayList<>(kept);

        int sortOrder = 1;

        for (ReviewImage image : active) {
            image.setSortOrder(sortOrder++);
            imageMapper.updateById(image);
        }

        if (newImages != null) {
            for (MultipartFile file : newImages) {
                StoredReviewImage stored =
                        imageStorageService.store(file);

                ReviewImage image = new ReviewImage();
                image.setReviewId(reviewId);
                image.setOriginalFilename(
                        stored.originalFilename()
                );
                image.setStorageProvider("LOCAL");
                image.setStorageKey(
                        stored.storageKey()
                );
                image.setImageUrl(
                        stored.imageUrl()
                );
                image.setThumbnailUrl(
                        stored.thumbnailUrl()
                );
                image.setMimeType(
                        stored.mimeType()
                );
                image.setFileSize(
                        stored.fileSize()
                );
                image.setWidth(stored.width());
                image.setHeight(stored.height());
                image.setContentHash(
                        stored.contentHash()
                );
                image.setSortOrder(sortOrder++);
                image.setStatus("ACTIVE");
                image.setCreatedAt(
                        OffsetDateTime.now()
                );

                imageMapper.insert(image);
                active.add(image);
            }
        }

        return active;
    }

    private void saveVersion(
            Review review,
            List<ReviewImage> images,
            String changeType
    ) {
        ReviewVersion version =
                new ReviewVersion();

        version.setReviewId(review.getId());
        version.setVersion(
                review.getCurrentVersion()
        );
        version.setRating(review.getRating());
        version.setTasteRating(
                review.getTasteRating()
        );
        version.setEnvironmentRating(
                review.getEnvironmentRating()
        );
        version.setServiceRating(
                review.getServiceRating()
        );
        version.setAverageSpend(
                review.getAverageSpend()
        );
        version.setConsumptionDate(
                review.getConsumptionDate()
        );
        version.setContent(review.getContent());
        version.setStatusSnapshot(
                review.getStatus()
        );
        version.setModerationStatusSnapshot(
                review.getModerationStatus()
        );
        version.setChangedBy(
                review.getUserId()
        );
        version.setChangeType(changeType);
        version.setCreatedAt(
                OffsetDateTime.now()
        );

        versionMapper.insert(version);
    }

    private void refreshMerchantRatingStats(
            Long merchantId
    ) {
        MerchantRatingSummaryVO summary =
                calculateMerchantRatingSummary(
                        merchantId
                );

        Merchant merchant =
                merchantMapper.selectById(merchantId);

        if (merchant == null) {
            return;
        }

        int reviewCount =
                summary.getRatingCount() == null
                        ? 0
                        : summary.getRatingCount().intValue();

        OffsetDateTime updatedAt =
                OffsetDateTime.now();

        merchantMapper.update(
                null,
                new LambdaUpdateWrapper<Merchant>()
                        .eq(
                                Merchant::getId,
                                merchantId
                        )
                        .set(
                                Merchant::getRating,
                                summary.getAverageRating()
                        )
                        .set(
                                Merchant::getReviewCount,
                                reviewCount
                        )
                        .set(
                                Merchant::getUpdatedAt,
                                updatedAt
                        )
        );
    }

    private void triggerAnalysisPlaceholder(
            Review review
    ) {
        ReviewAnalysis analysis =
                new ReviewAnalysis();

        analysis.setReviewId(review.getId());
        analysis.setReviewVersion(
                review.getCurrentVersion()
        );
        analysis.setAnalysisVersion(1);
        analysis.setSentiment("NEUTRAL");
        analysis.setLowConfidence(false);
        analysis.setKeywords("[]");
        analysis.setAspects("[]");
        analysis.setStatus("PENDING");
        analysis.setBusinessTraceId(
                "review-submit-"
                        + review.getId()
                        + "-v"
                        + review.getCurrentVersion()
        );
        analysis.setCreatedAt(
                OffsetDateTime.now()
        );
        analysis.setUpdatedAt(
                OffsetDateTime.now()
        );

        saveAnalysis(analysis);
    }

    private ReviewSubmitResponse toSubmitResponse(
            Review review,
            List<ReviewImage> images
    ) {
        ReviewSubmitResponse response =
                new ReviewSubmitResponse();

        response.setId(review.getId());
        response.setUserId(review.getUserId());
        response.setMerchantId(
                review.getMerchantId()
        );
        response.setContent(review.getContent());
        response.setRating(review.getRating());
        response.setTasteRating(
                review.getTasteRating()
        );
        response.setEnvironmentRating(
                review.getEnvironmentRating()
        );
        response.setServiceRating(
                review.getServiceRating()
        );
        response.setAverageSpend(
                review.getAverageSpend()
        );
        response.setConsumptionDate(
                review.getConsumptionDate()
        );
        response.setCurrentVersion(
                review.getCurrentVersion()
        );
        response.setStatus(review.getStatus());
        response.setModerationStatus(
                review.getModerationStatus()
        );
        response.setRiskLevel(
                review.getRiskLevel()
        );
        response.setImages(
                images.stream()
                        .map(this::toImageVO)
                        .toList()
        );

        return response;
    }

    private ReviewImageVO toImageVO(
            ReviewImage image
    ) {
        ReviewImageVO vo =
                new ReviewImageVO();

        vo.setId(image.getId());
        vo.setImageUrl(
                image.getImageUrl()
        );
        vo.setThumbnailUrl(
                image.getThumbnailUrl()
        );
        vo.setMimeType(
                image.getMimeType()
        );
        vo.setFileSize(
                image.getFileSize()
        );
        vo.setSortOrder(
                image.getSortOrder()
        );

        return vo;
    }

    private BigDecimal average(
            List<BigDecimal> values
    ) {
        List<BigDecimal> present =
                values.stream()
                        .filter(value -> value != null)
                        .toList();

        if (present.isEmpty()) {
            return null;
        }

        BigDecimal sum =
                present.stream()
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        return sum.divide(
                BigDecimal.valueOf(present.size()),
                1,
                RoundingMode.HALF_UP
        );
    }

    private LambdaQueryWrapper<Review> publicReviewWrapper(
            Long merchantId
    ) {
        return new LambdaQueryWrapper<Review>()
                .eq(
                        Review::getMerchantId,
                        merchantId
                )
                .eq(
                        Review::getReviewType,
                        "ORIGINAL"
                )
                .eq(
                        Review::getStatus,
                        "PUBLISHED"
                )
                .eq(
                        Review::getModerationStatus,
                        "APPROVED"
                );
    }

   // ==================== 标签统计与筛选 ====================

/**
 * 获取某个商家的评价标签统计。
 *
 * 查询逻辑：
 * 1. 从 review_tag_relations 表按 merchantId 聚合
 * 2. 只统计已发布且审核通过的评价（与公开列表口径一致）
 * 3. 按情感倾向（POSITIVE/NEUTRAL/NEGATIVE）分别计数
 * 4. 只返回至少关联一条公开评价的标签（totalCount > 0）
 * @param merchantId 商家 ID
 * @return 标签统计列表，按 totalCount 降序排列
 */
public List<ReviewTagStatVO> getMerchantReviewTags(Long merchantId) {
    // 1. 执行 SQL 查询，得到原始统计行（每行是 标签+情感 维度的计数）
    List<TagSentimentCount> rows = tagRelationMapper.countTagsByMerchant(merchantId);

    // 2. 按 tagCode 分组，将不同 sentiment 的计数合并成一个 VO
    Map<String, ReviewTagStatVO> grouped = new LinkedHashMap<>();

    for (TagSentimentCount row : rows) {
        String code = row.getTagCode();
        ReviewTagStatVO vo = grouped.computeIfAbsent(code, key ->
                ReviewTagStatVO.builder()
                        .tagCode(code)
                        .tagName(row.getTagName())
                        .category(row.getCategory())
                        .positiveCount(0L)
                        .neutralCount(0L)
                        .negativeCount(0L)
                        .totalCount(0L)
                        .build()
        );

        // 根据当前行的 sentiment 累加到对应计数器
        switch (row.getSentiment()) {
            case "POSITIVE" -> vo.setPositiveCount(row.getCnt());
            case "NEUTRAL"  -> vo.setNeutralCount(row.getCnt());
            case "NEGATIVE" -> vo.setNegativeCount(row.getCnt());
        }
        vo.setTotalCount(vo.getTotalCount() + row.getCnt());
    }

    // 3. 过滤掉计数为 0 的标签
    List<ReviewTagStatVO> result = new ArrayList<>(grouped.values());
    result.removeIf(vo -> vo.getTotalCount() == 0);

    // 4. 按总数降序，同数量按标签名升序
    result.sort((a, b) -> {
        int cmp = Long.compare(b.getTotalCount(), a.getTotalCount());
        return cmp != 0 ? cmp : a.getTagName().compareTo(b.getTagName());
    });

    return result;
}

/**
 * 获取单条评价关联的所有标签（含标签名、情感等展示信息）。
 *
 * 用于评价列表中每条评价的标签展示。
 *
 * @param reviewId 评价 ID
 * @return 该评价的标签关联列表
 */
public List<TagRelationWithName> getReviewTags(Long reviewId) {
    return tagRelationMapper.findTagsByReviewId(reviewId);
}

/**
 * 按商家分页查询公开评价，支持标签和情感倾向筛选。
 *
 * 两个筛选参数都是可选的：
 * - tagCode 不为空时，只返回关联了该标签的评价
 * - sentiment 不为空时，在 tagCode 筛选基础上进一步限定情感倾向
 * - 都不传时行为和原来一样（返回所有公开评价）
 *
 * @param merchantId 商家 ID
 * @param pageNum    页码（从 1 开始）
 * @param pageSize   每页条数
 * @param tagCode    可选，标签编码如 "TASTE_GOOD"
 * @param sentiment  可选，情感倾向 POSITIVE / NEUTRAL / NEGATIVE
 * @return 分页结果
 */
public Page<Review> listByMerchant(
        Long merchantId,
        int pageNum,
        int pageSize,
        String tagCode,
        String sentiment
) {
    // 如果没有标签筛选条件，走原来的简单查询
    if (tagCode == null || tagCode.isBlank()) {
        return listByMerchant(merchantId, pageNum, pageSize);
    }

    // 有标签筛选条件时，需要通过子查询找到符合条件的 review_id 列表
    // 再用这些 ID 去查评价主表，保证分页逻辑正确
    Page<Review> page = Page.of(pageNum, pageSize);

    // 先查出该 tagCode 对应的 tagId
    ReviewTag tag = tagMapper.selectOne(
            new LambdaQueryWrapper<ReviewTag>()
                    .eq(ReviewTag::getCode, tagCode)
                    .eq(ReviewTag::getStatus, "ACTIVE")
    );

    // 标签不存在或已禁用 → 返回空结果
    if (tag == null) {
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }

    // 查 review_tag_relations 表，找到符合条件的 review_id 集合
    LambdaQueryWrapper<ReviewTagRelation> relationWrapper = new LambdaQueryWrapper<>();
    relationWrapper.eq(ReviewTagRelation::getTagId, tag.getId());

    // 如果指定了情感倾向，进一步过滤
    if (sentiment != null && !sentiment.isBlank()) {
        relationWrapper.eq(ReviewTagRelation::getSentiment, sentiment.toUpperCase());
    }

    List<Long> filteredReviewIds = tagRelationMapper.selectList(relationWrapper)
            .stream()
            .map(ReviewTagRelation::getReviewId)
            .distinct()
            .toList();

    // 没有匹配的评价
    if (filteredReviewIds.isEmpty()) {
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }

    // 用筛选出的 ID 列表，加上公开评价条件，分页查询
    LambdaQueryWrapper<Review> reviewWrapper = new LambdaQueryWrapper<>();
    reviewWrapper.in(Review::getId, filteredReviewIds)
            .eq(Review::getMerchantId, merchantId)
            .eq(Review::getStatus, "PUBLISHED")
            .eq(Review::getModerationStatus, "APPROVED")
            .orderByDesc(Review::getPublishedAt);

    return this.page(page, reviewWrapper);
}
//==============================================

    private ApiException badRequest(
            String code,
            String message
    ) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                code,
                message
        );
    }
}
