package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 评论-标签关联表（多对多）
 */
@Data
@TableName("review_tag_relations")
public class ReviewTagRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    /** 评价版本号 */
    private Integer reviewVersion;

    private Long tagId;

    /** POSITIVE / NEUTRAL / NEGATIVE */
    private String sentiment;

    private BigDecimal confidence;
    private String evidenceText;
    private String modelName;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
