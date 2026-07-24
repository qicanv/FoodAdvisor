package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.AnalysisFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 商家分析结果反馈 Mapper（EPIC-06 Story 5）
 */
@Mapper
public interface AnalysisFeedbackMapper extends BaseMapper<AnalysisFeedback> {

    /**
     * 按分析类型和反馈类型统计数量（管理员汇总用）。
     */
    @Select("""
            SELECT
                analysis_type AS "analysisType",
                feedback_type AS "feedbackType",
                COUNT(*)     AS "count"
            FROM analysis_feedback
            GROUP BY analysis_type, feedback_type
            ORDER BY analysis_type, feedback_type
            """)
    List<Map<String, Object>> countByAnalysisTypeAndFeedbackType();

    /**
     * 按分析类型统计（管理员汇总用）。
     */
    @Select("""
            SELECT
                analysis_type AS "analysisType",
                COUNT(*)     AS "totalCount",
                COUNT(*) FILTER (WHERE feedback_type = 'ACCURATE')   AS "accurateCount",
                COUNT(*) FILTER (WHERE feedback_type = 'INACCURATE') AS "inaccurateCount"
            FROM analysis_feedback
            GROUP BY analysis_type
            ORDER BY analysis_type
            """)
    List<Map<String, Object>> statisticsByAnalysisType();

    /**
     * 查询某商家对指定分析记录是否已有反馈。
     */
    @Select("""
            SELECT COUNT(*)
            FROM analysis_feedback
            WHERE merchant_id = #{merchantId}
              AND analysis_type = #{analysisType}
              AND analysis_id = #{analysisId}
            """)
    int countByMerchantAndAnalysis(
            @Param("merchantId") Long merchantId,
            @Param("analysisType") String analysisType,
            @Param("analysisId") Long analysisId
    );
}
