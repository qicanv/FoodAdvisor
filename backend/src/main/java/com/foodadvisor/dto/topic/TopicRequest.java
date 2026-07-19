package com.foodadvisor.dto.topic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopicRequest {

    private String name;
    private String description;
    private String coverUrl;
    private String status;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private List<Long> merchantIds;
    private List<String> tagNames;
}