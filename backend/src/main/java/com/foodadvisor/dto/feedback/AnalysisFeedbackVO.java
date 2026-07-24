package com.foodadvisor.dto.feedback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 商家分析结果反馈视图对象（EPIC-06 Story 5）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisFeedbackVO {

    /** 反馈记录ID */
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /** 商家名称（管理员查看时展示） */
    private String merchantName;

    /** 分析类型 */
    private String analysisType;

    /** 分析类型中文名 */
    private String analysisTypeText;

    /** 关联的分析记录ID */
    private Long analysisId;

    /** 反馈类型：ACCURATE / INACCURATE */
    private String feedbackType;

    /** 反馈类型中文名 */
    private String feedbackTypeText;

    /** 具体问题说明 */
    private String content;

    /** 提交反馈的商家用户ID */
    private Long createdBy;

    /** 提交反馈的用户名 */
    private String createdByUsername;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
