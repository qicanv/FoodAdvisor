package com.foodadvisor.dto.highlight;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 商家亮点展示 VO（EPIC-02 Story 5）
 *
 * 返回给前端的亮点数据，包含亮点基本信息及
 * 溯源所需的最小评价信息。
 */
public class MerchantHighlightVO {

    /** 亮点ID */
    private Long highlightId;

    /** 商家ID */
    private Long merchantId;

    /**
     * 亮点类型：
     * SIGNATURE_DISH / ENVIRONMENT / SERVICE / PRICE / BRAND_FEATURE
     */
    private String highlightType;

    /** 亮点标题 */
    private String title;

    /** 亮点详细描述 */
    private String description;

    /** 提及该亮点的正面评价数量 */
    private Integer mentionCount;

    /** 好评占比（0~1） */
    private BigDecimal positiveRatio;

    /** 亮点版本号 */
    private Integer version;

    /** ACTIVE / OUTDATED / DISABLED */
    private String status;

    /** 生成时间 */
    private OffsetDateTime generatedAt;

    // ======= 前端提示信息 =======

    /** 当前状态的人类可读说明，如"数据不足，无法生成亮点" */
    private String statusMessage;

    /** 生成亮点所需的最低正面评价数 */
    private Integer minimumReviewCount;

    /** 当前商家的有效正面评价总数 */
    private Integer availablePositiveCount;

    // ======= 依据溯源 =======

    /** 亮点依据列表（可选，查看溯源时填充） */
    private List<HighlightEvidenceVO> evidences;

    // ==================== Getters / Setters ====================

    public Long getHighlightId() {
        return highlightId;
    }

    public void setHighlightId(Long highlightId) {
        this.highlightId = highlightId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getHighlightType() {
        return highlightType;
    }

    public void setHighlightType(String highlightType) {
        this.highlightType = highlightType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getMentionCount() {
        return mentionCount;
    }

    public void setMentionCount(Integer mentionCount) {
        this.mentionCount = mentionCount;
    }

    public BigDecimal getPositiveRatio() {
        return positiveRatio;
    }

    public void setPositiveRatio(BigDecimal positiveRatio) {
        this.positiveRatio = positiveRatio;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Integer getMinimumReviewCount() {
        return minimumReviewCount;
    }

    public void setMinimumReviewCount(Integer minimumReviewCount) {
        this.minimumReviewCount = minimumReviewCount;
    }

    public Integer getAvailablePositiveCount() {
        return availablePositiveCount;
    }

    public void setAvailablePositiveCount(Integer availablePositiveCount) {
        this.availablePositiveCount = availablePositiveCount;
    }

    public List<HighlightEvidenceVO> getEvidences() {
        return evidences;
    }

    public void setEvidences(List<HighlightEvidenceVO> evidences) {
        this.evidences = evidences;
    }
}
