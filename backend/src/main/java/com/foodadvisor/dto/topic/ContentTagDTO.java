package com.foodadvisor.dto.topic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentTagDTO {

    private Long id;
    private String code;
    private String name;
    private String category;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer count;
}