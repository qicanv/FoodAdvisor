package com.foodadvisor.storage;

public record StoredReviewImage(
        String originalFilename,
        String storageKey,
        String imageUrl,
        String thumbnailUrl,
        String mimeType,
        long fileSize,
        Integer width,
        Integer height,
        String contentHash
) {
}
