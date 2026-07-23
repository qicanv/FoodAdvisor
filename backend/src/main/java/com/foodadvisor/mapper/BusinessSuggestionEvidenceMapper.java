package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.BusinessSuggestionEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 经营改进建议依据 Mapper（EPIC-02 Story 8）
 */
@Mapper
public interface BusinessSuggestionEvidenceMapper extends BaseMapper<BusinessSuggestionEvidence> {

    /**
     * 查询某条建议的所有依据。
     */
    @Select("""
            SELECT id, suggestion_id, source_type, source_id,
                   review_id, metric_snapshot, evidence_excerpt, created_at
            FROM business_suggestion_evidences
            WHERE suggestion_id = #{suggestionId}
            ORDER BY id
            """)
    List<BusinessSuggestionEvidence> selectBySuggestionId(
            @Param("suggestionId") Long suggestionId);

    /**
     * 批量查询多条建议的所有依据。
     */
    @Select("""
            <script>
            SELECT id, suggestion_id, source_type, source_id,
                   review_id, metric_snapshot, evidence_excerpt, created_at
            FROM business_suggestion_evidences
            WHERE suggestion_id IN
            <foreach collection="suggestionIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            ORDER BY suggestion_id, id
            </script>
            """)
    List<BusinessSuggestionEvidence> selectBySuggestionIds(
            @Param("suggestionIds") List<Long> suggestionIds);
}
