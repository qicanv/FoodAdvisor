package com.foodadvisor.dto.prompt;

import jakarta.validation.constraints.Size;

public record PromptVersionSwitchRequest(

        @Size(max = 500)
        String operationNote
) {
}