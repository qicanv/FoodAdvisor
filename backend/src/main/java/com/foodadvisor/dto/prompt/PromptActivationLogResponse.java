package com.foodadvisor.dto.prompt;

import java.time.OffsetDateTime;

public record PromptActivationLogResponse(
        Long id,
        String sceneCode,

        Long fromVersionId,
        String fromVersionTag,

        Long toVersionId,
        String toVersionTag,

        String operationType,
        String operationNote,
        Long operatedBy,
        OffsetDateTime operatedAt
) {
}