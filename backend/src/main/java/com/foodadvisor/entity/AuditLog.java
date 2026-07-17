package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName(value = "audit_logs", autoResultMap = true)
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operationType;

    private Long operatorUserId;

    private String operatorUsername;

    private String operatorRole;

    private String module;

    private String level;

    private String result;

    private String objectType;

    private String objectId;

    private String errorCode;

    private String errorMessage;

    private String requestMethod;

    private String requestUri;

    private String ipAddress;

    private String userAgent;

    private String businessTraceId;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
