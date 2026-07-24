package com.foodadvisor.dto.session;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ChatSessionSummaryResponse {

    private Long sessionId;

    private String title;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}