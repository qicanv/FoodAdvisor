package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    @Select("SELECT COUNT(*) FROM audit_logs WHERE actor_role = #{actorRole} AND action = #{action} AND created_at >= #{startTime} AND created_at <= #{endTime}")
    Long countByActorRoleAndAction(@Param("actorRole") String actorRole, @Param("action") String action, @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);
}