package com.foodadvisor.dto.review;

import lombok.Data;

@Data
public class ReviewImageVO {

    private Long id;
    private String imageUrl;
    private String thumbnailUrl;
    private String mimeType;
    private Long fileSize;
    private Integer sortOrder;
}
