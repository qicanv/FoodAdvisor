package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.ContentRiskRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 内容风险检测记录 Mapper
 */
@Mapper
public interface ContentRiskRecordMapper extends BaseMapper<ContentRiskRecord> {

    /**
     * 查询指定内容的最新检测记录
     */
    @Select("SELECT * FROM content_risk_records " +
            "WHERE content_type = #{contentType} AND content_id = #{contentId} " +
            "ORDER BY created_at DESC LIMIT 1")
    ContentRiskRecord findLatest(
            @Param("contentType") String contentType,
            @Param("contentId") Long contentId);

    /**
     * 查询指定内容的所有检测记录
     */
    @Select("SELECT * FROM content_risk_records " +
            "WHERE content_type = #{contentType} AND content_id = #{contentId} " +
            "ORDER BY created_at DESC")
    List<ContentRiskRecord> findByContent(
            @Param("contentType") String contentType,
            @Param("contentId") Long contentId);

    /**
     * 按风险等级统计检测记录数
     */
    @Select("SELECT risk_level, COUNT(*) as cnt " +
            "FROM content_risk_records " +
            "WHERE created_at >= #{since} " +
            "GROUP BY risk_level")
    List<java.util.Map<String, Object>> countByRiskLevel(
            @Param("since") java.time.OffsetDateTime since);
}
