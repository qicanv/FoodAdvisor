package com.foodadvisor.dto.prompt;

import java.time.OffsetDateTime;

public record PromptDefinitionResponse(
        Long id,
        String sceneCode,
        String sceneName,
        String description,
        String status,

        Long activeVersionId,
        Integer activeVersionNo,
        String activeVersionTag,
        String activeVersionContent,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}