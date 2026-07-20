package com.foodadvisor.dto.trace;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;

@Data
public class AiTraceQueryRequest {
    private String traceId;
    private String requestId;
    private Long sessionId;
    private Long userId;
    private String scene;
    private String status;
    private String modelName;
    private Boolean fallback;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endTime;
    private int pageNum = 1;
    private int pageSize = 20;
}
