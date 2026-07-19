package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {
    @Select("SELECT COUNT(*) FROM reviews WHERE review_time >= #{startTime} AND review_time <= #{endTime}")
    Long countByTimeRange(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);
}