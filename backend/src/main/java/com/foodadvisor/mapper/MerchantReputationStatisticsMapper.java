package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.MerchantReputationStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 商家口碑统计 Mapper。
 * 除 MyBatis-Plus 基础 CRUD 外，提供按商家 + 周期类型的时间范围查询。
 */
@Mapper
public interface MerchantReputationStatisticsMapper extends BaseMapper<MerchantReputationStatistics> {

    /**
     * 查询某商家在指定周期类型下、指定日期范围内的所有统计记录，按时间升序排列。
     *
     * @param merchantId 商家 ID
     * @param periodType 周期类型：DAY / WEEK / MONTH
     * @param startDate  查询起始日期（含），null 表示不限制
     * @param endDate    查询结束日期（含），null 表示不限制
     * @return 按 period_start 升序排列的统计记录列表
     */
    @Select("""
            SELECT *
            FROM merchant_reputation_statistics
            WHERE merchant_id = #{merchantId}
              AND period_type = #{periodType}
              AND (#{startDate}::date IS NULL OR period_end >= #{startDate}::date)
              AND (#{endDate}::date IS NULL OR period_start <= #{endDate}::date)
            ORDER BY period_start ASC
            """)
    List<MerchantReputationStatistics> findByMerchantAndPeriod(
            @Param("merchantId") Long merchantId,
            @Param("periodType") String periodType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 删除某商家某个周期类型下、某个具体周期的已有统计记录。
     * 用于 refresh 时先删后插，保证幂等。
     *
     * @param merchantId  商家 ID
     * @param periodType  周期类型
     * @param periodStart 周期起始日期
     * @param periodEnd   周期结束日期
     * @return 删除行数
     */
    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM merchant_reputation_statistics
            WHERE merchant_id = #{merchantId}
              AND period_type = #{periodType}
              AND period_start = #{periodStart}::date
              AND period_end = #{periodEnd}::date
            """)
    int deleteByPeriod(
            @Param("merchantId") Long merchantId,
            @Param("periodType") String periodType,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    /**
     * 查询某商家最新一条统计记录的日期，用于判断增量刷新起点。
     *
     * @param merchantId 商家 ID
     * @param periodType 周期类型
     * @return 最新统计的周期结束日期，无记录时返回 null
     */
    @Select("""
            SELECT period_end
            FROM merchant_reputation_statistics
            WHERE merchant_id = #{merchantId}
              AND period_type = #{periodType}
            ORDER BY period_end DESC
            LIMIT 1
            """)
    LocalDate findLatestPeriodEnd(
            @Param("merchantId") Long merchantId,
            @Param("periodType") String periodType
    );
}
