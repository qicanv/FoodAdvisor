package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("review_images")
public class ReviewImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;
    private String originalFilename;
    private String storageProvider;
    private String storageKey;
    private String imageUrl;
    private String thumbnailUrl;
    private String mimeType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String contentHash;
    private Integer sortOrder;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime deletedAt;
}
