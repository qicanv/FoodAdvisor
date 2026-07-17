package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName(value = "recommendation_items", autoResultMap = true)
public class RecommendationItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recommendationId;

    private Long merchantId;

    private Integer rankNo;

    /**
     * 数据库存储范围为 0～1。
     * 例如接口显示 86.50 分时，此处保存 0.865000。
     */
    private BigDecimal score;

    /**
     * JSONB：菜系、评分、价格、距离、环境和口碑等评分明细。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String scoreDetails;

    /**
     * JSONB：主要匹配条件。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String matchedConditions;

    /**
     * JSONB：未匹配条件、风险提示和数据缺失说明。
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String unmatchedConditions;

    /**
     * 根据系统计算结果生成的模板化推荐理由。
     */
    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}