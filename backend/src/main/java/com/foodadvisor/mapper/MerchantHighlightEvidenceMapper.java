package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.MerchantHighlightEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商家亮点依据 Mapper（EPIC-02 Story 5）
 */
@Mapper
public interface MerchantHighlightEvidenceMapper extends BaseMapper<MerchantHighlightEvidence> {
    @Select("""
            <script>
            SELECT id, highlight_id, review_id, review_version,
                   evidence_excerpt, created_at
            FROM merchant_highlight_evidences
            WHERE highlight_id IN
              <foreach collection="highlightIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY highlight_id, id
            </script>
            """)
    List<MerchantHighlightEvidence> selectByHighlightIds(
            @Param("highlightIds") List<Long> highlightIds);
}
