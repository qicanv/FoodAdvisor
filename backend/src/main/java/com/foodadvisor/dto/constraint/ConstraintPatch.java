package com.foodadvisor.dto.constraint;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ConstraintPatch {
    private String intent = "MERCHANT_RECOMMENDATION";
    private Boolean directRecommend = false;
    private ConstraintPatchOperations operations =
            new ConstraintPatchOperations();
    private List<ConstraintConflictVO> conflicts = new ArrayList<>();
    private List<String> followUpHints = new ArrayList<>();
    private Map<String, BigDecimal> confidence = new LinkedHashMap<>();
}
