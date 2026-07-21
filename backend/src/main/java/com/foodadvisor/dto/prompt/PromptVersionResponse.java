package com.foodadvisor.dto.prompt;

import java.time.OffsetDateTime;

public record PromptVersionResponse(
        Long id,
        Long promptDefinitionId,
        String sceneCode,
        Integer versionNo,
        String versionTag,
        String content,
        String changeNote,
        Long createdBy,
        OffsetDateTime createdAt,
        boolean active
) {
}