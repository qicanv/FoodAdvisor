package com.foodadvisor.controller;

import com.foodadvisor.common.ApiResponse;
import com.foodadvisor.dto.constraint.ConstraintExtractRequest;
import com.foodadvisor.dto.constraint.ConstraintExtractResponse;
import com.foodadvisor.service.ConstraintExtractionService;
import com.foodadvisor.util.AuthenticatedUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 消费需求提取接口。
 */
@RestController
@RequestMapping("/api/diner/sessions")
public class ConstraintController {

    private final ConstraintExtractionService
            constraintExtractionService;

    public ConstraintController(
            ConstraintExtractionService
                    constraintExtractionService
    ) {
        this.constraintExtractionService =
                constraintExtractionService;
    }

    /**
     * 从用户本轮消息中提取消费条件，
     * 并与当前会话的历史条件进行合并。
     */
    @PostMapping("/{sessionId}/constraints/extract")
    public ApiResponse<ConstraintExtractResponse> extract(
            @PathVariable Long sessionId,
            @Valid
            @RequestBody
            ConstraintExtractRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        ConstraintExtractResponse response =
                constraintExtractionService
                        .extractAndMerge(
                                sessionId,
                                AuthenticatedUserId.require(
                                        httpRequest
                                ),
                                request.getMessage()
                        );
        httpResponse.setHeader("X-Trace-Id", response.getTraceId());

        return ApiResponse.success(response);
    }
}
