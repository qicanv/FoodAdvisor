package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.UserBehaviorLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserBehaviorLogMapper extends BaseMapper<UserBehaviorLog> {

    @Select("SELECT search_keyword as keyword, COUNT(*) as count FROM user_behavior_logs " +
            "WHERE event_type = 'SEARCH' AND created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND search_keyword IS NOT NULL AND search_keyword != '' " +
            "GROUP BY search_keyword ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> getHotSearchKeywords(@Param("startTime") OffsetDateTime startTime,
                                                    @Param("endTime") OffsetDateTime endTime,
                                                    @Param("limit") Integer limit);

    @Select("SELECT scene_type as scene, COUNT(*) as count FROM user_behavior_logs " +
            "WHERE event_type = 'SCENE_ENTRY' AND created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND scene_type IS NOT NULL AND scene_type != '' " +
            "GROUP BY scene_type ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> getHotScenes(@Param("startTime") OffsetDateTime startTime,
                                            @Param("endTime") OffsetDateTime endTime,
                                            @Param("limit") Integer limit);

    @Select("SELECT ubl.merchant_id as merchantId, m.name as merchantName, COUNT(*) as count FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE ubl.event_type = 'MERCHANT_CLICK' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND ubl.merchant_id IS NOT NULL " +
            "GROUP BY ubl.merchant_id, m.name ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> getHotMerchants(@Param("startTime") OffsetDateTime startTime,
                                               @Param("endTime") OffsetDateTime endTime,
                                               @Param("limit") Integer limit);

    @Select("SELECT event_type, COUNT(*) as count FROM user_behavior_logs " +
            "WHERE created_at BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY event_type ORDER BY count DESC")
    List<Map<String, Object>> getEventStats(@Param("startTime") OffsetDateTime startTime,
                                             @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT DATE(created_at) as date, event_type, COUNT(*) as count FROM user_behavior_logs " +
            "WHERE created_at BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY DATE(created_at), event_type ORDER BY DATE(created_at)")
    List<Map<String, Object>> getDailyStats(@Param("startTime") OffsetDateTime startTime,
                                             @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT tag_code as tag, COUNT(*) as count FROM user_behavior_logs " +
            "WHERE event_type = 'TAG_CLICK' AND created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND tag_code IS NOT NULL AND tag_code != '' " +
            "GROUP BY tag_code ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> getHotTags(@Param("startTime") OffsetDateTime startTime,
                                          @Param("endTime") OffsetDateTime endTime,
                                          @Param("limit") Integer limit);

    @Select("SELECT ct.code as tag, ct.name as tagName, COALESCE(tc.count, 0) as count FROM content_tags ct " +
            "LEFT JOIN (SELECT tag_code, COUNT(*) as count FROM user_behavior_logs ubl " +
            "            WHERE ubl.event_type = 'TAG_CLICK' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "            AND ubl.tag_code IS NOT NULL AND ubl.tag_code != '' " +
            "            GROUP BY ubl.tag_code) tc ON ct.code = tc.tag_code " +
            "WHERE ct.status = 'ACTIVE' ORDER BY count DESC")
    List<Map<String, Object>> getAllTagsWithClickCount(@Param("startTime") OffsetDateTime startTime,
                                                       @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs WHERE created_at BETWEEN #{startTime} AND #{endTime}")
    Long getTotalEvents(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(DISTINCT user_id) FROM user_behavior_logs WHERE created_at BETWEEN #{startTime} AND #{endTime}")
    Long getActiveUsers(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs WHERE event_type = 'FEEDBACK' AND created_at BETWEEN #{startTime} AND #{endTime}")
    Long getFeedbackCount(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs WHERE event_type = 'FEEDBACK' AND feedback_type = #{feedbackType} AND created_at BETWEEN #{startTime} AND #{endTime}")
    Long getFeedbackCountByType(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime, @Param("feedbackType") String feedbackType);

    @Select("SELECT COUNT(*) FROM user_behavior_logs WHERE event_type = 'SEARCH' AND created_at BETWEEN #{startTime} AND #{endTime}")
    Long getSearchCount(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs WHERE event_type IN ('MERCHANT_CLICK', 'TOPIC_CLICK', 'TAG_CLICK', 'SCENE_ENTRY') AND created_at BETWEEN #{startTime} AND #{endTime}")
    Long getClickCount(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT ubl.merchant_id as merchantId, m.name as merchantName, m.cuisine as cuisine, COUNT(*) as count " +
            "FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type = 'MERCHANT_CLICK' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND ubl.merchant_id IS NOT NULL AND m.region_code = #{regionCode} " +
            "GROUP BY ubl.merchant_id, m.name, m.cuisine ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> getRegionalHotMerchants(@Param("regionCode") String regionCode,
                                                       @Param("startTime") OffsetDateTime startTime,
                                                       @Param("endTime") OffsetDateTime endTime,
                                                       @Param("limit") Integer limit);

    @Select("SELECT m.cuisine as cuisine, COUNT(*) as count FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type = 'MERCHANT_CLICK' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND m.cuisine IS NOT NULL AND m.cuisine != '' AND m.region_code = #{regionCode} " +
            "GROUP BY m.cuisine ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> getRegionalHotCuisines(@Param("regionCode") String regionCode,
                                                      @Param("startTime") OffsetDateTime startTime,
                                                      @Param("endTime") OffsetDateTime endTime,
                                                      @Param("limit") Integer limit);

    @Select("SELECT ubl.search_keyword as keyword, COUNT(*) as count FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type = 'SEARCH' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND ubl.search_keyword IS NOT NULL AND ubl.search_keyword != '' " +
            "AND (m.region_code = #{regionCode} OR m.region_code IS NULL) " +
            "GROUP BY ubl.search_keyword ORDER BY count DESC LIMIT #{limit}")
    List<Map<String, Object>> getRegionalHotKeywords(@Param("regionCode") String regionCode,
                                                      @Param("startTime") OffsetDateTime startTime,
                                                      @Param("endTime") OffsetDateTime endTime,
                                                      @Param("limit") Integer limit);

    @Select("SELECT CASE " +
            "WHEN HOUR(ubl.created_at) BETWEEN 6 AND 9 THEN '早餐时段' " +
            "WHEN HOUR(ubl.created_at) BETWEEN 10 AND 14 THEN '午餐时段' " +
            "WHEN HOUR(ubl.created_at) BETWEEN 14 AND 17 THEN '下午茶时段' " +
            "WHEN HOUR(ubl.created_at) BETWEEN 17 AND 21 THEN '晚餐时段' " +
            "WHEN HOUR(ubl.created_at) BETWEEN 21 AND 24 THEN '夜宵时段' " +
            "ELSE '凌晨时段' END as period, COUNT(*) as count " +
            "FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type IN ('SEARCH', 'MERCHANT_CLICK', 'SCENE_ENTRY') AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND (m.region_code = #{regionCode} OR m.region_code IS NULL) " +
            "GROUP BY period ORDER BY count DESC")
    List<Map<String, Object>> getRegionalConsumptionPeriods(@Param("regionCode") String regionCode,
                                                             @Param("startTime") OffsetDateTime startTime,
                                                             @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND (m.region_code = #{regionCode} OR m.region_code IS NULL)")
    Long getRegionalTotalEvents(@Param("regionCode") String regionCode,
                                 @Param("startTime") OffsetDateTime startTime,
                                 @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(DISTINCT ubl.user_id) FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND (m.region_code = #{regionCode} OR m.region_code IS NULL)")
    Long getRegionalActiveUsers(@Param("regionCode") String regionCode,
                                 @Param("startTime") OffsetDateTime startTime,
                                 @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type = 'MERCHANT_CLICK' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND m.region_code = #{regionCode}")
    Long getRegionalMerchantClicks(@Param("regionCode") String regionCode,
                                    @Param("startTime") OffsetDateTime startTime,
                                    @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type = 'SEARCH' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND (m.region_code = #{regionCode} OR m.region_code IS NULL)")
    Long getRegionalSearches(@Param("regionCode") String regionCode,
                              @Param("startTime") OffsetDateTime startTime,
                              @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type = 'SCENE_ENTRY' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND (m.region_code = #{regionCode} OR m.region_code IS NULL)")
    Long getRegionalSceneEntries(@Param("regionCode") String regionCode,
                                  @Param("startTime") OffsetDateTime startTime,
                                  @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type = 'TAG_CLICK' AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND (m.region_code = #{regionCode} OR m.region_code IS NULL)")
    Long getRegionalTagClicks(@Param("regionCode") String regionCode,
                               @Param("startTime") OffsetDateTime startTime,
                               @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT DATE(ubl.created_at) as date, COUNT(*) as count FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type IN ('SEARCH', 'MERCHANT_CLICK', 'SCENE_ENTRY') AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND (m.region_code = #{regionCode} OR m.region_code IS NULL) " +
            "GROUP BY DATE(ubl.created_at) ORDER BY DATE(ubl.created_at)")
    List<Map<String, Object>> getRegionalDailyStats(@Param("regionCode") String regionCode,
                                                     @Param("startTime") OffsetDateTime startTime,
                                                     @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT ct.name as cuisine, COUNT(DISTINCT ubl.id) as count " +
            "FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "LEFT JOIN topic_merchants tm ON m.id = tm.merchant_id " +
            "LEFT JOIN topic_tags tt ON tm.topic_id = tt.topic_id " +
            "LEFT JOIN content_tags ct ON tt.tag_id = ct.id " +
            "WHERE ubl.event_type = 'MERCHANT_CLICK' " +
            "AND ubl.created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND ct.id IS NOT NULL " +
            "GROUP BY ct.name ORDER BY count DESC")
    List<Map<String, Object>> getSearchByCuisine(@Param("startTime") OffsetDateTime startTime,
                                                  @Param("endTime") OffsetDateTime endTime);
}
