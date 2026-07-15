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

    public ReviewService(
            ReviewAnalysisMapper analysisMapper,
            ReviewTagRelationMapper tagRelationMapper,
            ReviewImageMapper imageMapper,
            ReviewVersionMapper versionMapper,
            MerchantMapper merchantMapper,
            ReviewImageStorageService imageStorageService,
            JdbcTemplate jdbcTemplate
    ) {
        this.analysisMapper = analysisMapper;
        this.tagRelationMapper = tagRelationMapper;
        this.imageMapper = imageMapper;
        this.versionMapper = versionMapper;
        this.merchantMapper = merchantMapper;
        this.imageStorageService = imageStorageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按商家分页查询公开评价。
     */
    public Page<Review> listByMerchant(
            Long merchantId,
            int pageNum,
            int pageSize
    ) {
        Page<Review> page = Page.of(pageNum, pageSize);

        LambdaQueryWrapper<Review> wrapper =
                new LambdaQueryWrapper<>();

        wrapper.eq(Review::getMerchantId, merchantId)
                .eq(Review::getStatus, "PUBLISHED")
                .orderByDesc(Review::getPublishedAt);

        return this.page(page, wrapper);
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

        review.setRating(request.getRating());
        review.setTasteRating(request.getTasteRating());
        review.setEnvironmentRating(
                request.getEnvironmentRating()
        );
        review.setServiceRating(
                request.getServiceRating()
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
            List<Integer> values
    ) {
        List<Integer> present =
                values.stream()
                        .filter(value -> value != null)
                        .toList();

        if (present.isEmpty()) {
            return null;
        }

        BigDecimal sum =
                present.stream()
                        .map(BigDecimal::valueOf)
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