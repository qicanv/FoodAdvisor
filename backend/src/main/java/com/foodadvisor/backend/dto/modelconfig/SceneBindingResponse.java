package com.foodadvisor.backend.dto.modelconfig;

import java.time.OffsetDateTime;

public record SceneBindingResponse(
        Long id,
        String sceneType,
        Long modelConfigId,
        String modelConfigName,
        String modelName,
        String status,
        OffsetDateTime updatedAt
) {
}
