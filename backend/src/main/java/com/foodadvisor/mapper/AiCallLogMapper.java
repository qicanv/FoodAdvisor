package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
    @Select("SELECT COUNT(*) FROM ai_call_logs WHERE created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countByTimeRange(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);
    @Select("SELECT COUNT(*) FROM ai_call_logs WHERE function_type = #{functionType} AND created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countByFunctionTypeAndTime(@Param("functionType") String functionType, @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);
}