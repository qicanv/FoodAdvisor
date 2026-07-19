package com.foodadvisor.dto.highlight;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 亮点依据 VO — 关联原始评价的溯源信息（EPIC-02 Story 5 验收准则 4）
 *
 * 用户点击某条亮点后，可查看支撑该结论的原始评价片段。
 * 当原评价被删除/隐藏时，标记不可用并不再展示原文。
 */
public class HighlightEvidenceVO {

    /** 评价ID */
    private Long reviewId;

    /** 评价版本号 */
    private Integer reviewVersion;

    /** 评价原文中的依据片段 */
    private String evidenceExcerpt;

    /** 原评价是否仍可用（未被删除/隐藏） */
    private Boolean reviewAvailable;

    /** 评价综合评分（原评价可用时有值） */
    private BigDecimal rating;

    /** 评价正文全文（原评价可用时有值） */
    private String reviewContent;

    /** 评价发布时间 */
    private OffsetDateTime publishedAt;

    // ==================== Getters / Setters ====================

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public Integer getReviewVersion() {
        return reviewVersion;
    }

    public void setReviewVersion(Integer reviewVersion) {
        this.reviewVersion = reviewVersion;
    }

    public String getEvidenceExcerpt() {
        return evidenceExcerpt;
    }

    public void setEvidenceExcerpt(String evidenceExcerpt) {
        this.evidenceExcerpt = evidenceExcerpt;
    }

    public Boolean getReviewAvailable() {
        return reviewAvailable;
    }

    public void setReviewAvailable(Boolean reviewAvailable) {
        this.reviewAvailable = reviewAvailable;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public String getReviewContent() {
        return reviewContent;
    }

    public void setReviewContent(String reviewContent) {
        this.reviewContent = reviewContent;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
