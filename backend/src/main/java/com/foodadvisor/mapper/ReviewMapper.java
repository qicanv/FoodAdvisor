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

    @Select("SELECT COUNT(*) FROM reviews WHERE deleted_at IS NULL")
    Long countTotalReviews();

    @Select("<script>" +
            "SELECT r.id, r.content, r.rating, r.risk_level as \"riskLevel\", r.moderation_status as \"moderationStatus\", r.status, r.created_at as \"createdAt\", " +
            "r.moderation_operator as \"moderationOperator\", m.name as \"merchantName\", m.region_code as \"regionCode\", u.nickname as \"userNickname\", u.username " +
            "FROM reviews r " +
            "LEFT JOIN merchants m ON r.merchant_id = m.id " +
            "LEFT JOIN users u ON r.user_id = u.id " +
            "WHERE r.deleted_at IS NULL " +
            "<if test='riskLevel != null and riskLevel != \"\"'>AND r.risk_level = #{riskLevel}</if> " +
            "<if test='riskLevel == null or riskLevel == \"\"'>AND r.risk_level IN ('MEDIUM', 'HIGH')</if> " +
            "<if test='moderationStatus != null and moderationStatus != \"\"'>AND r.moderation_status = #{moderationStatus}</if> " +
            "<if test='merchantId != null'>AND r.merchant_id = #{merchantId}</if> " +
            "<if test='startTime != null'>AND r.created_at >= #{startTime}</if> " +
            "<if test='endTime != null'>AND r.created_at &lt;= #{endTime}</if> " +
            "ORDER BY CASE WHEN r.moderation_status = 'PENDING' THEN 0 ELSE 1 END, CASE WHEN r.risk_level = 'HIGH' THEN 0 WHEN r.risk_level = 'MEDIUM' THEN 1 ELSE 2 END, r.created_at DESC " +
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
            "<if test='riskLevel == null or riskLevel == \"\"'>AND r.risk_level IN ('MEDIUM', 'HIGH')</if> " +
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

    @Select("SELECT r.id, r.content, r.rating, r.taste_rating as \"tasteRating\", r.environment_rating as \"environmentRating\", r.service_rating as \"serviceRating\", " +
            "r.average_spend as \"averageSpend\", r.consumption_date as \"consumptionDate\", r.risk_level as \"riskLevel\", r.moderation_status as \"moderationStatus\", r.status, " +
            "r.created_at as \"createdAt\", r.review_time as \"reviewTime\", r.source, r.review_type as \"reviewType\", r.moderation_operator as \"moderationOperator\", " +
            "m.id as \"merchantId\", m.name as \"merchantName\", m.category, m.cuisine, m.address, m.region_code as \"regionCode\", " +
            "u.id as \"userId\", u.nickname as \"userNickname\", u.username, u.phone, u.email " +
            "FROM reviews r " +
            "LEFT JOIN merchants m ON r.merchant_id = m.id " +
            "LEFT JOIN users u ON r.user_id = u.id " +
            "WHERE r.id = #{id} AND r.deleted_at IS NULL")
    Map<String, Object> getReviewDetailWithRelations(@Param("id") Long id);

    @Update("UPDATE reviews SET moderation_status = #{moderationStatus}, status = #{status}, " +
            "moderation_operator = #{moderationOperator}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateModerationStatus(@Param("id") Long id,
                               @Param("moderationStatus") String moderationStatus,
                               @Param("status") String status,
                               @Param("moderationOperator") String moderationOperator);

    @Update("UPDATE reviews SET risk_level = #{riskLevel}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateRiskLevel(@Param("id") Long id, @Param("riskLevel") String riskLevel);

    @Select("SELECT moderation_status FROM reviews WHERE id = #{id} AND deleted_at IS NULL")
    String getCurrentModerationStatus(@Param("id") Long id);

    @Select("SELECT id, name FROM merchants WHERE platform_status = 'ACTIVE' ORDER BY name")
    List<Map<String, Object>> getActiveMerchants();

    @Select("SELECT COUNT(*) FROM reviews WHERE moderation_status = 'PENDING' AND risk_level IN ('MEDIUM', 'HIGH') AND deleted_at IS NULL")
    Long countPendingReviews();

    @Select("SELECT COUNT(*) FROM reviews WHERE risk_level = 'HIGH' AND deleted_at IS NULL")
    Long countHighRiskReviews();

    @Select("SELECT COUNT(*) FROM reviews WHERE risk_level = 'MEDIUM' AND deleted_at IS NULL")
    Long countMediumRiskReviews();
}