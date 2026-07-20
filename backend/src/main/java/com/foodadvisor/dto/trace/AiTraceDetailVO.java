package com.foodadvisor.dto.trace;

import com.foodadvisor.entity.AiRequestTrace;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.entity.AiTraceRetrievalSource;
import lombok.Data;
import java.util.List;

@Data
public class AiTraceDetailVO {
    private AiRequestTrace trace;
    private List<AiRequestTraceStage> stages;
    private List<AiTraceRetrievalSource> retrievalSources;
}
