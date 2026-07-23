package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT COUNT(DISTINCT id) FROM users WHERE updated_at >= #{startTime} AND updated_at <= #{endTime}")
    Long countActiveUsers(@Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL")
    Long countTotalUsers();
}