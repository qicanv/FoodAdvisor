package com.foodadvisor.dto.dialogue;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DialogueHistoryResponse {

    private Long sessionId;

    private List<DialogueMessageVO> messages =
            new ArrayList<>();
}
