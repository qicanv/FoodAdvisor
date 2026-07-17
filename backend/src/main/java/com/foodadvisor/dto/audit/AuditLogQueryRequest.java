package com.foodadvisor.dto.audit;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@Data
public class AuditLogQueryRequest {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endTime;

    private Long operatorUserId;

    private String operatorUsername;

    private String module;

    private String level;

    private String operationType;

    private String result;

    private String objectType;

    private String objectId;

    private int pageNum = 1;

    private int pageSize = 10;
}
