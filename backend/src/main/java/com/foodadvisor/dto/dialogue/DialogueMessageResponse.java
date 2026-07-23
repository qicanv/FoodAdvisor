package com.foodadvisor.dto.dialogue;

import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DialogueMessageResponse {
    private String traceId;

    private Long sessionId;

    private Long userMessageId;

    private Long assistantMessageId;

    private String requestId;

    private String responseType;

    private String assistantText;

    private String conversationStage;

    private ConstraintState currentConstraints;

    private List<String> missingFields =
            new ArrayList<>();

    private RecommendationRankResponse recommendation;

    private Boolean degraded;

    private String extractor;
    private String modelName;
    private String promptVersion;
    private String replyGenerator = "TEMPLATE_FALLBACK";
    private String replyPromptVersion;
    private Boolean replyDegraded = true;
}
