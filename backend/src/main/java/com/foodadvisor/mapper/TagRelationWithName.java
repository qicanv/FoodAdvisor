package com.foodadvisor.mapper;

/**
 * 内部使用：评价标签 JOIN 查询的返回行映射。
 * 比 ReviewTagRelation 多了 tagCode / tagName / category，方便直接展示。
 */
public class TagRelationWithName {
    private Long id;
    private Long reviewId;
    private Integer reviewVersion;
    private Long tagId;
    private String sentiment;
    private java.math.BigDecimal confidence;
    private String evidenceText;
    private String tagCode;
    private String tagName;
    private String category;

    // getter / setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReviewId() { return reviewId; }
    public void setReviewId(Long reviewId) { this.reviewId = reviewId; }

    public Integer getReviewVersion() { return reviewVersion; }
    public void setReviewVersion(Integer reviewVersion) { this.reviewVersion = reviewVersion; }

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public java.math.BigDecimal getConfidence() { return confidence; }
    public void setConfidence(java.math.BigDecimal confidence) { this.confidence = confidence; }

    public String getEvidenceText() { return evidenceText; }
    public void setEvidenceText(String evidenceText) { this.evidenceText = evidenceText; }

    public String getTagCode() { return tagCode; }
    public void setTagCode(String tagCode) { this.tagCode = tagCode; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
