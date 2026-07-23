package com.foodadvisor.dto.ai;

import com.foodadvisor.dto.constraint.ConstraintState;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class DialogueExtractAiRequest {

    private Long sessionId;

    private Long messageId;

    private String content;

    private ConstraintState currentConstraints;

    private List<Map<String, String>> recentMessages = new ArrayList<>();
    private List<String> rejectedFields = new ArrayList<>();
    private List<Map<String, Object>> pendingConflicts = new ArrayList<>();
    private String timezone = "Asia/Shanghai";
}
