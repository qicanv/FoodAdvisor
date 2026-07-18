package com.foodadvisor.backend.exception;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.service.AuditLogService;
import com.foodadvisor.util.SensitiveLogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditLogService auditLogService;
    private final SensitiveLogSanitizer sanitizer;

    public GlobalExceptionHandler() {
        this.auditLogService = null;
        this.sanitizer = new SensitiveLogSanitizer();
    }

    @Autowired
    public GlobalExceptionHandler(
            AuditLogService auditLogService,
            SensitiveLogSanitizer sanitizer
    ) {
        this.auditLogService = auditLogService;
        this.sanitizer = sanitizer;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>>
    handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError
                : exception.getBindingResult().getFieldErrors()) {
            errors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        ApiResponse<Map<String, String>> response =
                new ApiResponse<>(
                        "INVALID_REQUEST",
                        "Request validation failed",
                        errors
                );

        ResponseEntity<ApiResponse<Map<String, String>>> result =
                ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);

        recordApiException(
                request,
                "WARN",
                "INVALID_REQUEST",
                exception
        );

        return result;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        ApiResponse<Void> response = ApiResponse.failure(
                exception.getCode(),
                exception.getMessage()
        );

        ResponseEntity<ApiResponse<Void>> result = ResponseEntity
                .status(exception.getStatus())
                .body(response);

        recordApiException(
                request,
                exception.getStatus().is5xxServerError()
                        ? "ERROR"
                        : "WARN",
                exception.getCode(),
                exception
        );

        return result;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>>
    handleUnknownException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled server exception: {}",
                errorSummary(exception)
        );

        ApiResponse<Void> response = ApiResponse.failure(
                "INTERNAL_ERROR",
                "Internal server error"
        );

        ResponseEntity<ApiResponse<Void>> result = ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);

        recordApiException(
                request,
                "ERROR",
                "INTERNAL_ERROR",
                exception
        );

        return result;
    }

    private void recordApiException(
            HttpServletRequest request,
            String level,
            String errorCode,
            Exception exception
    ) {
        if (auditLogService == null || request == null) {
            return;
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setOperationType("API_EXCEPTION");
        auditLog.setModule(resolveModule(request));
        auditLog.setLevel(level);
        auditLog.setResult("FAILURE");
        auditLog.setOperatorUserId(toLong(request.getAttribute("userId")));
        auditLog.setOperatorUsername(toStringValue(
                request.getAttribute("username")
        ));
        auditLog.setOperatorRole(toStringValue(request.getAttribute("role")));
        auditLog.setErrorCode(errorCode);
        auditLog.setErrorMessage(errorSummary(exception));
        auditLog.setRequestMethod(request.getMethod());
        auditLog.setRequestUri(requestUri(request));
        auditLog.setIpAddress(clientIp(request));
        auditLog.setUserAgent(request.getHeader("User-Agent"));
        auditLog.setMetadata(
                "{\"exceptionType\":\""
                        + jsonEscape(exception.getClass().getSimpleName())
                        + "\"}"
        );

        try {
            auditLogService.recordSafely(auditLog);
        } catch (Exception loggingException) {
            log.warn(
                    "API exception audit logging failed: {}",
                    loggingException.getClass().getSimpleName()
            );
        }
    }

    private String resolveModule(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "API";
        }
        if (uri.startsWith("/api/auth")) {
            return "AUTH";
        }
        if (uri.startsWith("/api/admin")) {
            return "ADMIN";
        }
        if (uri.startsWith("/api/reviews")) {
            return "REVIEW";
        }
        if (uri.startsWith("/api/diner")
                || uri.startsWith("/api/dialogue")) {
            return "RECOMMENDATION";
        }
        return "API";
    }

    private String requestUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return sanitizer.sanitize(uri);
        }
        return sanitizer.sanitize(uri + "?" + query);
    }

    private String errorSummary(Exception exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null ? "" : ": " + message);
        summary = summary.replaceAll("(?is)SQL\\s*\\[.*?]", "SQL [****]");
        summary = sanitizer.sanitize(summary);
        return summary.length() > 300
                ? summary.substring(0, 300)
                : summary;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Long toLong(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
