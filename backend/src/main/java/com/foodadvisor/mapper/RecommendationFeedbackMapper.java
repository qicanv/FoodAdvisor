package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.RecommendationFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface RecommendationFeedbackMapper extends BaseMapper<RecommendationFeedback> {
    @Select("""
            SELECT COUNT(*)
            FROM recommendation_feedback
            WHERE created_at >= #{startTime}
              AND created_at <= #{endTime}
            """)
    Long countByTimeRange(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);
}
