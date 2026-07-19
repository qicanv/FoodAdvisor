package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 商家亮点依据 — 亮点结论与原始评价的溯源关联（EPIC-02 Story 5 验收准则 2/4）
 *
 * 每条亮点至少关联一条真实正面评论，用户可点击亮点
 * 查看对应的原始评价内容，确保所有结论可追溯。
 */
@Data
@TableName("merchant_highlight_evidences")
public class MerchantHighlightEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的亮点ID */
    private Long highlightId;

    /** 原始评价ID */
    private Long reviewId;

    /** 评价版本号（快照当时版本） */
    private Integer reviewVersion;

    /** 评价原文中的依据片段（截取，不改写） */
    private String evidenceExcerpt;

    /** 创建时间 */
    private OffsetDateTime createdAt;
}
