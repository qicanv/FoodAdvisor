package com.foodadvisor.dto.audit;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AuditLogVO {

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

    private String metadata;

    private OffsetDateTime createdAt;
}
