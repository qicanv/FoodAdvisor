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

    @Select("SELECT merchant_id as merchantId, m.name as merchantName, COUNT(*) as count FROM user_behavior_logs ubl " +
            "LEFT JOIN merchants m ON ubl.merchant_id = m.id " +
            "WHERE event_type = 'MERCHANT_CLICK' AND created_at BETWEEN #{startTime} AND #{endTime} " +
            "AND merchant_id IS NOT NULL " +
            "GROUP BY merchant_id, m.name ORDER BY count DESC LIMIT #{limit}")
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

    @Select("SELECT COUNT(*) FROM user_behavior_logs WHERE created_at BETWEEN #{startTime} AND #{endTime}")
    Long getTotalEvents(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(DISTINCT user_id) FROM user_behavior_logs WHERE created_at BETWEEN #{startTime} AND #{endTime}")
    Long getActiveUsers(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);
}
