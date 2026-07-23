package com.foodadvisor.dto.ai;

import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.constraint.ConstraintPatch;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DialogueExtractAiResponse {

    private String intent;

    private ConstraintState extractedConstraints;

    private List<String> clearedFields = new ArrayList<>();

    private BigDecimal confidence;

    private String extractor;

    private Boolean degraded;

    private String modelName;
    private String modelVersion;
    private String promptVersion;

    private String provider;

    private ConstraintPatch patch;
}
