package com.foodadvisor.dto.dialogue;

import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.AdjustmentSuggestionVO;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DialogueMessageVO {

    private Long id;

    private String role;

    private String content;

    private String requestId;

    private String responseType;

    private Long recommendationId;

    private String status;

    private List<RecommendationItemVO> recommendations =
            new ArrayList<>();

    private List<AdjustmentSuggestionVO> adjustmentSuggestions =
            new ArrayList<>();

    private OffsetDateTime createdAt;
}
