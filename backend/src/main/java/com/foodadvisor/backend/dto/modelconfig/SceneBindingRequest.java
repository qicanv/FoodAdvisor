package com.foodadvisor.backend.dto.modelconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SceneBindingRequest(
        @NotBlank
        String sceneType,

        @NotNull
        Long modelConfigId
) {
}
