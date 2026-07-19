package com.foodadvisor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodadvisor.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    @Select("""
            SELECT COUNT(*)
            FROM audit_logs
            WHERE operator_role = #{operatorRole}
              AND operation_type = #{operationType}
              AND created_at >= #{startTime}
              AND created_at <= #{endTime}
            """)
    Long countByOperatorRoleAndOperationType(
            @Param("operatorRole") String operatorRole,
            @Param("operationType") String operationType,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );
}
