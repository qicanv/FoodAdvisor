package com.foodadvisor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 刷评案例查询 Mapper（纯 MyBatis，不继承 BaseMapper，不触发 MP 实体 PK 解析）
 */
@Mapper
public interface FraudCaseQueryMapper {

    @Select("<script>" +
            "SELECT id AS case_id, merchant_id, rule_type, risk_level, status, " +
            "matched_rule_snapshot::text AS matched_rule_snapshot, " +
            "matched_review_ids::text AS matched_review_ids, " +
            "summary, detected_at, reviewed_by, reviewed_at, " +
            "review_conclusion, review_remark " +
            "FROM review_fraud_cases WHERE 1=1 " +
            "<if test='status != null and status != \"\"'>AND status = #{status}</if> " +
            "<if test='riskLevel != null and riskLevel != \"\"'>AND risk_level = #{riskLevel}</if> " +
            "<if test='ruleType != null and ruleType != \"\"'>AND rule_type = #{ruleType}</if> " +
            "<if test='merchantId != null'>AND merchant_id = #{merchantId}</if> " +
            "ORDER BY detected_at DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Map<String, Object>> findCaseMaps(
            @Param("status") String status,
            @Param("riskLevel") String riskLevel,
            @Param("ruleType") String ruleType,
            @Param("merchantId") Long merchantId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM review_fraud_cases WHERE 1=1 " +
            "<if test='status != null and status != \"\"'>AND status = #{status}</if> " +
            "<if test='riskLevel != null and riskLevel != \"\"'>AND risk_level = #{riskLevel}</if> " +
            "<if test='ruleType != null and ruleType != \"\"'>AND rule_type = #{ruleType}</if> " +
            "<if test='merchantId != null'>AND merchant_id = #{merchantId}</if>" +
            "</script>")
    long countCases(
            @Param("status") String status,
            @Param("riskLevel") String riskLevel,
            @Param("ruleType") String ruleType,
            @Param("merchantId") Long merchantId);
}
