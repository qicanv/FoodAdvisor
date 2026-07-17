package com.foodadvisor.dto.ai;

import com.foodadvisor.dto.constraint.ConstraintState;
import lombok.Data;

@Data
public class DialogueExtractAiRequest {

    private Long sessionId;

    private Long messageId;

    private String content;

    private ConstraintState currentConstraints;
}
