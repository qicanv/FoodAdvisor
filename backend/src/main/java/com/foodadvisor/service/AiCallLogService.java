package com.foodadvisor.service;

import com.foodadvisor.entity.AiCallLog;
import com.foodadvisor.mapper.AiCallLogMapper;
import com.foodadvisor.util.SensitiveLogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiCallLogService {

    private static final Logger log =
            LoggerFactory.getLogger(AiCallLogService.class);

    private final AiCallLogMapper aiCallLogMapper;
    private final SensitiveLogSanitizer sanitizer;

    public AiCallLogService(
            AiCallLogMapper aiCallLogMapper,
            SensitiveLogSanitizer sanitizer
    ) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.sanitizer = sanitizer;
    }

    public void record(AiCallLog aiCallLog) {
        aiCallLogMapper.insert(aiCallLog);
    }

    public void recordSafely(AiCallLog aiCallLog) {
        try {
            record(aiCallLog);
        } catch (Exception exception) {
            String traceId = aiCallLog == null
                    ? null
                    : sanitizer.sanitize(aiCallLog.getTraceId());
            String functionType = aiCallLog == null
                    ? null
                    : sanitizer.sanitize(aiCallLog.getFunctionType());
            String errorMessage =
                    sanitizer.sanitize(exception.getMessage());

            log.warn(
                    "AI call log write failed. traceId={}, functionType={}, error={}",
                    traceId,
                    functionType,
                    errorMessage
            );
        }
    }
}
