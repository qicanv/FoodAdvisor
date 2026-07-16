package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.ReviewTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评论-标签关联表 Mapper。
 * 基础 CRUD 由 MyBatis-Plus BaseMapper 自动提供。
 */
@Mapper
public interface ReviewTagRelationMapper extends BaseMapper<ReviewTagRelation> {

    /**
     * 按商家统计各标签在不同情感倾向下的评价数量。
     *
     * 只统计状态为 PUBLISHED 且审核通过的评价，保证前端展示的标签数量
     * 和公开评价列表一致。
     *
     * 返回的每一行是一个 (tagCode, tagName, category, sentiment, count) 元组，
     * 由 Service 层聚合成 ReviewTagStatVO 列表。
     *
     * @param merchantId 商家 ID
     * @return 标签-情感维度统计行列表
     */
    @Select("""
        SELECT
            rt.code        AS tagCode,
            rt.name        AS tagName,
            rt.category    AS category,
            rtr.sentiment  AS sentiment,
            COUNT(*)       AS cnt
        FROM review_tag_relations rtr
        JOIN review_tags rt  ON rtr.tag_id = rt.id
        JOIN reviews r       ON rtr.review_id = r.id
        WHERE r.merchant_id       = #{merchantId}
          AND r.status            = 'PUBLISHED'
          AND r.moderation_status = 'APPROVED'
          AND r.deleted_at        IS NULL
          AND rt.status           = 'ACTIVE'
        GROUP BY rt.code, rt.name, rt.category, rtr.sentiment
        ORDER BY rt.category, COUNT(*) DESC
    """)
    List<TagSentimentCount> countTagsByMerchant(@Param("merchantId") Long merchantId);

    /**
     * 查询单条评价关联的所有标签（含标签名称等字典信息）。
     * 用于在评价列表中展示每条评价带了哪些标签。
     *
     * @param reviewId 评价 ID
     * @return 该评价的标签关联列表（含 JOIN 后的标签名）
     */
    @Select("""
        SELECT
            rtr.id,
            rtr.review_id,
            rtr.review_version,
            rtr.tag_id,
            rtr.sentiment,
            rtr.confidence,
            rtr.evidence_text,
            rt.code   AS tagCode,
            rt.name   AS tagName,
            rt.category
        FROM review_tag_relations rtr
        JOIN review_tags rt ON rtr.tag_id = rt.id
        WHERE rtr.review_id = #{reviewId}
          AND rt.status = 'ACTIVE'
    """)
    List<TagRelationWithName> findTagsByReviewId(@Param("reviewId") Long reviewId);
}
