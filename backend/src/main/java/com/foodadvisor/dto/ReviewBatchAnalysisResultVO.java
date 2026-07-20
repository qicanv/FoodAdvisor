package com.foodadvisor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReviewBatchAnalysisResultVO {
    private String traceId;
    private int requestedCount;
    private int successCount;
    private int failedCount;
    private int skippedCount;
    private List<Long> analysisIds = new ArrayList<>();
}
