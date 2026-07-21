package com.foodadvisor.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePromptVersionRequest(

        @NotBlank
        @Size(max = 50000)
        String content,

        @NotBlank
        @Size(max = 500)
        String changeNote,

        boolean activate
) {
}