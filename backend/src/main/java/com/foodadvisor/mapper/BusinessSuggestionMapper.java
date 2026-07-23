package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.BusinessSuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 经营改进建议 Mapper（EPIC-02 Story 8）
 */
@Mapper
public interface BusinessSuggestionMapper extends BaseMapper<BusinessSuggestion> {

    /**
     * 查询商家的活跃建议列表，按优先级和生成时间排序。
     */
    @Select("""
            SELECT id, merchant_id, version, title, description,
                   category, priority, timeframe, expected_effect,
                   data_basis_type, data_basis_summary,
                   metric_name, metric_value, confidence,
                   status, generated_at
            FROM business_suggestions
            WHERE merchant_id = #{merchantId}
              AND status = 'ACTIVE'
            ORDER BY
                CASE priority
                    WHEN 'HIGH' THEN 1
                    WHEN 'MEDIUM' THEN 2
                    WHEN 'LOW' THEN 3
                END,
                generated_at DESC
            """)
    List<BusinessSuggestion> selectActiveByMerchantId(
            @Param("merchantId") Long merchantId);

    /**
     * 获取商家建议的最大版本号。
     */
    @Select("""
            SELECT COALESCE(MAX(version), 0)
            FROM business_suggestions
            WHERE merchant_id = #{merchantId}
            """)
    int selectMaxVersion(@Param("merchantId") Long merchantId);
}
