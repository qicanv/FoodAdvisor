package com.foodadvisor.dto.feedback;

import lombok.Data;

/**
 * 商家分析结果反馈提交请求（EPIC-06 Story 5）
 */
@Data
public class AnalysisFeedbackSubmitRequest {

    /**
     * 分析类型（必填）：
     * SENTIMENT / KEYWORD / ISSUE_ATTRIBUTION / COMPETITOR /
     * BUSINESS_SUGGESTION / REVIEW_SUMMARY / HIGHLIGHT
     */
    private String analysisType;

    /** 关联的分析记录ID（选填，不填表示对该类型的整体反馈） */
    private Long analysisId;

    /** 反馈类型（必填）：ACCURATE / INACCURATE */
    private String feedbackType;

    /** 具体问题说明（选填，最多 2000 字符） */
    private String content;
}
