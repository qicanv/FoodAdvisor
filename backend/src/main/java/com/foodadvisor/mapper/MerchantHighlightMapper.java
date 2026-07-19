package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.MerchantHighlight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 商家亮点 Mapper（EPIC-02 Story 5）
 */
@Mapper
public interface MerchantHighlightMapper extends BaseMapper<MerchantHighlight> {
    @Select("""
            <script>
            SELECT id, merchant_id, highlight_type, title, description,
                   mention_count, positive_ratio, version, status, generated_at
            FROM merchant_highlights
            WHERE status = 'ACTIVE'
              AND merchant_id IN
              <foreach collection="merchantIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY merchant_id, mention_count DESC, positive_ratio DESC, id
            </script>
            """)
    List<MerchantHighlight> selectActiveByMerchantIds(
            @Param("merchantIds") List<Long> merchantIds);
}
