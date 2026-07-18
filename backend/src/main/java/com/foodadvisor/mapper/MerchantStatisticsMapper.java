package com.foodadvisor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MerchantStatisticsMapper {

    @Select("SELECT COUNT(*) FROM merchants WHERE platform_status = 'ACTIVE'")
    Long countTotalMerchants();

    @Select("SELECT COUNT(DISTINCT m.id) FROM merchants m " +
            "JOIN audit_logs al ON al.object_id = m.id::text " +
            "WHERE m.platform_status = 'ACTIVE' " +
            "AND al.operator_role = 'MERCHANT' " +
            "AND al.created_at >= #{startTime} AND al.created_at <= #{endTime}")
    Long countActiveMerchants(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(DISTINCT m.id) FROM merchants m " +
            "JOIN audit_logs al ON al.object_id = m.id::text " +
            "WHERE m.platform_status = 'ACTIVE' " +
            "AND m.id = #{merchantId} " +
            "AND al.operator_role = 'MERCHANT' " +
            "AND al.created_at >= #{startTime} AND al.created_at <= #{endTime}")
    Long countActiveMerchantById(@Param("merchantId") Long merchantId, @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM reviews WHERE merchant_id = #{merchantId} AND status = 'PUBLISHED' AND review_type = 'ORIGINAL'")
    Long countPublishedReviewsByMerchant(@Param("merchantId") Long merchantId);

    @Select("SELECT COUNT(DISTINCT r.parent_review_id) FROM reviews r " +
            "WHERE r.merchant_id = #{merchantId} AND r.status = 'PUBLISHED' AND r.review_type = 'FOLLOW_UP'")
    Long countRepliedReviewsByMerchant(@Param("merchantId") Long merchantId);

    @Select("SELECT COUNT(*) FROM reviews WHERE status = 'PUBLISHED' AND review_type = 'ORIGINAL'")
    Long countTotalPublishedReviews();

    @Select("SELECT COUNT(DISTINCT r.parent_review_id) FROM reviews r WHERE r.status = 'PUBLISHED' AND r.review_type = 'FOLLOW_UP'")
    Long countTotalRepliedReviews();

    @Select("SELECT COUNT(*) FROM ai_call_logs " +
            "WHERE function_type = 'REPUTATION_ANALYSIS' " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countReputationAnalysisCalls(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM ai_call_logs " +
            "WHERE function_type = 'REPUTATION_ANALYSIS' " +
            "AND user_id IN (SELECT user_id FROM merchant_members WHERE merchant_id = #{merchantId}) " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countReputationAnalysisCallsByMerchant(@Param("merchantId") Long merchantId, @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM ai_call_logs " +
            "WHERE function_type = 'COMPETITOR_ANALYSIS' " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countCompetitorAnalysisCalls(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM ai_call_logs " +
            "WHERE function_type = 'COMPETITOR_ANALYSIS' " +
            "AND user_id IN (SELECT user_id FROM merchant_members WHERE merchant_id = #{merchantId}) " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countCompetitorAnalysisCallsByMerchant(@Param("merchantId") Long merchantId, @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM ai_call_logs " +
            "WHERE function_type = 'BUSINESS_ADVICE' " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countBusinessAdviceCalls(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM ai_call_logs " +
            "WHERE function_type = 'BUSINESS_ADVICE' " +
            "AND user_id IN (SELECT user_id FROM merchant_members WHERE merchant_id = #{merchantId}) " +
            "AND created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countBusinessAdviceCallsByMerchant(@Param("merchantId") Long merchantId, @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT DATE_TRUNC('day', al.created_at) as date, COUNT(DISTINCT al.object_id) as count " +
            "FROM audit_logs al " +
            "JOIN merchants m ON al.object_id = m.id::text " +
            "WHERE al.operator_role = 'MERCHANT' " +
            "AND m.platform_status = 'ACTIVE' " +
            "AND al.created_at >= #{startTime} AND al.created_at <= #{endTime} " +
            "GROUP BY DATE_TRUNC('day', al.created_at) " +
            "ORDER BY date")
    List<Map<String, Object>> getActiveMerchantTrend(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT DATE_TRUNC('day', acl.created_at) as date, COUNT(*) as count " +
            "FROM ai_call_logs acl " +
            "WHERE acl.function_type = 'REPUTATION_ANALYSIS' " +
            "AND acl.created_at >= #{startTime} AND acl.created_at <= #{endTime} " +
            "GROUP BY DATE_TRUNC('day', acl.created_at) " +
            "ORDER BY date")
    List<Map<String, Object>> getReputationAnalysisTrend(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT DATE_TRUNC('day', acl.created_at) as date, COUNT(*) as count " +
            "FROM ai_call_logs acl " +
            "WHERE acl.function_type = 'COMPETITOR_ANALYSIS' " +
            "AND acl.created_at >= #{startTime} AND acl.created_at <= #{endTime} " +
            "GROUP BY DATE_TRUNC('day', acl.created_at) " +
            "ORDER BY date")
    List<Map<String, Object>> getCompetitorAnalysisTrend(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT DATE_TRUNC('day', acl.created_at) as date, COUNT(*) as count " +
            "FROM ai_call_logs acl " +
            "WHERE acl.function_type = 'BUSINESS_ADVICE' " +
            "AND acl.created_at >= #{startTime} AND acl.created_at <= #{endTime} " +
            "GROUP BY DATE_TRUNC('day', acl.created_at) " +
            "ORDER BY date")
    List<Map<String, Object>> getBusinessAdviceTrend(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);
}