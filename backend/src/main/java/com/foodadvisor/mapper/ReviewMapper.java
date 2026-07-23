package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
    @Select("SELECT COUNT(*) FROM reviews WHERE review_time >= #{startTime} AND review_time <= #{endTime}")
    Long countByTimeRange(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("<script>" +
            "SELECT r.id, r.content, r.rating, r.risk_level, r.moderation_status, r.status, r.created_at, " +
            "r.review_time, r.source, m.name as merchant_name, m.region_code, u.nickname as user_nickname, u.username " +
            "FROM reviews r " +
            "LEFT JOIN merchants m ON r.merchant_id = m.id " +
            "LEFT JOIN users u ON r.user_id = u.id " +
            "WHERE r.deleted_at IS NULL " +
            "<if test='riskLevel != null and riskLevel != \"\"'>AND r.risk_level = #{riskLevel}</if> " +
            "<if test='moderationStatus != null and moderationStatus != \"\"'>AND r.moderation_status = #{moderationStatus}</if> " +
            "<if test='merchantId != null'>AND r.merchant_id = #{merchantId}</if> " +
            "<if test='startTime != null'>AND r.created_at >= #{startTime}</if> " +
            "<if test='endTime != null'>AND r.created_at &lt;= #{endTime}</if> " +
            "ORDER BY r.created_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Map<String, Object>> getModerationList(
            @Param("riskLevel") String riskLevel,
            @Param("moderationStatus") String moderationStatus,
            @Param("merchantId") Long merchantId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset);

    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM reviews r " +
            "WHERE r.deleted_at IS NULL " +
            "<if test='riskLevel != null and riskLevel != \"\"'>AND r.risk_level = #{riskLevel}</if> " +
            "<if test='moderationStatus != null and moderationStatus != \"\"'>AND r.moderation_status = #{moderationStatus}</if> " +
            "<if test='merchantId != null'>AND r.merchant_id = #{merchantId}</if> " +
            "<if test='startTime != null'>AND r.created_at >= #{startTime}</if> " +
            "<if test='endTime != null'>AND r.created_at &lt;= #{endTime}</if>" +
            "</script>")
    Long countModerationList(
            @Param("riskLevel") String riskLevel,
            @Param("moderationStatus") String moderationStatus,
            @Param("merchantId") Long merchantId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT r.id, r.content, r.rating, r.taste_rating, r.environment_rating, r.service_rating, " +
            "r.average_spend, r.consumption_date, r.risk_level, r.moderation_status, r.status, " +
            "r.created_at, r.review_time, r.source, r.review_type, " +
            "m.id as merchant_id, m.name as merchant_name, m.category, m.cuisine, m.address, m.region_code, " +
            "u.id as user_id, u.nickname as user_nickname, u.username, u.phone, u.email " +
            "FROM reviews r " +
            "LEFT JOIN merchants m ON r.merchant_id = m.id " +
            "LEFT JOIN users u ON r.user_id = u.id " +
            "WHERE r.id = #{id} AND r.deleted_at IS NULL")
    Map<String, Object> getReviewDetailWithRelations(@Param("id") Long id);

    @Update("UPDATE reviews SET moderation_status = #{moderationStatus}, status = #{status}, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateModerationStatus(@Param("id") Long id,
                               @Param("moderationStatus") String moderationStatus,
                               @Param("status") String status);

    @Select("SELECT moderation_status FROM reviews WHERE id = #{id} AND deleted_at IS NULL")
    String getCurrentModerationStatus(@Param("id") Long id);

    @Select("SELECT id, name FROM merchants WHERE platform_status = 'ACTIVE' ORDER BY name")
    List<Map<String, Object>> getActiveMerchants();

    @Select("SELECT COUNT(*) FROM reviews WHERE moderation_status = 'PENDING' AND deleted_at IS NULL")
    Long countPendingReviews();

    // ===== 以下为刷评检测（FraudDetectionService）所需方法 =====

    @Select("<script>" +
            "SELECT merchant_id, COUNT(*) AS cnt FROM reviews " +
            "WHERE deleted_at IS NULL AND created_at >= #{since} " +
            "<if test='targetMerchantId != null'>AND merchant_id = #{targetMerchantId}</if> " +
            "GROUP BY merchant_id HAVING cnt >= #{thresholdCount}" +
            "</script>")
    List<Map<String, Object>> countReviewsByMerchantSince(
            @Param("since") OffsetDateTime since,
            @Param("thresholdCount") int thresholdCount,
            @Param("targetMerchantId") Long targetMerchantId);

    @Select("SELECT id FROM reviews WHERE deleted_at IS NULL " +
            "AND merchant_id = #{merchantId} AND created_at >= #{since} " +
            "ORDER BY created_at DESC")
    List<Long> getReviewIdsByMerchantSince(
            @Param("merchantId") Long merchantId,
            @Param("since") OffsetDateTime since);

    @Select("SELECT id, content FROM reviews WHERE deleted_at IS NULL " +
            "AND merchant_id = #{merchantId} AND created_at >= #{since} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<Map<String, Object>> getRecentReviewContents(
            @Param("merchantId") Long merchantId,
            @Param("since") OffsetDateTime since,
            @Param("limit") int limit);

    @Select("SELECT user_id, COUNT(*) AS cnt FROM reviews " +
            "WHERE deleted_at IS NULL AND created_at >= #{since} " +
            "GROUP BY user_id HAVING cnt >= #{thresholdCount}")
    List<Map<String, Object>> countReviewsByUserSince(
            @Param("since") OffsetDateTime since,
            @Param("thresholdCount") int thresholdCount);

    @Select("SELECT id FROM reviews WHERE deleted_at IS NULL " +
            "AND user_id = #{userId} AND created_at >= #{since} " +
            "ORDER BY created_at DESC")
    List<Long> getReviewIdsByUserSince(
            @Param("userId") Long userId,
            @Param("since") OffsetDateTime since);

    @Select("SELECT rating, COUNT(*) AS cnt FROM reviews " +
            "WHERE deleted_at IS NULL AND merchant_id = #{merchantId} " +
            "AND created_at >= #{since} " +
            "GROUP BY rating")
    List<Map<String, Object>> getRatingDistribution(
            @Param("merchantId") Long merchantId,
            @Param("since") OffsetDateTime since);

    @Update("UPDATE reviews SET risk_level = #{riskLevel}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{reviewId}")
    int updateReviewRiskLevel(
            @Param("reviewId") Long reviewId,
            @Param("riskLevel") String riskLevel);
}