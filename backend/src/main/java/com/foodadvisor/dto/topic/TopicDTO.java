package com.foodadvisor.dto.topic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicDTO {

    private Long id;
    private String name;
    private String description;
    private String coverUrl;
    private String status;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer merchantCount;
    private List<String> tags;
}