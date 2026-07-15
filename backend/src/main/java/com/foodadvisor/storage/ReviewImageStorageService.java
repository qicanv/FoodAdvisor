package com.foodadvisor.storage;

import com.foodadvisor.backend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReviewImageStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path uploadRoot;

    public ReviewImageStorageService(
            @Value("${foodadvisor.upload.review-image-dir:uploads/reviews}")
            String reviewImageDir
    ) {
        this.uploadRoot = Path.of(reviewImageDir).toAbsolutePath().normalize();
    }

    public StoredReviewImage store(MultipartFile file) {
        validate(file);

        String mimeType = file.getContentType();
        String extension = EXTENSIONS.get(mimeType);
        LocalDate today = LocalDate.now();
        String fileName = UUID.randomUUID() + extension;
        Path relativeDir = Path.of(
                String.valueOf(today.getYear()),
                String.format("%02d", today.getMonthValue()),
                String.format("%02d", today.getDayOfMonth())
        );
        Path targetDir = uploadRoot.resolve(relativeDir).normalize();
        Path target = targetDir.resolve(fileName).normalize();

        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);

            ImageInfo imageInfo = readImageInfo(target, mimeType);
            Path thumbnail = createThumbnail(target, targetDir, fileName, mimeType);
            String storageKey = relativeDir.resolve(fileName)
                    .toString()
                    .replace("\\", "/");
            String thumbnailKey = relativeDir.resolve(thumbnail.getFileName())
                    .toString()
                    .replace("\\", "/");

            return new StoredReviewImage(
                    file.getOriginalFilename(),
                    storageKey,
                    "/uploads/reviews/" + storageKey,
                    "/uploads/reviews/" + thumbnailKey,
                    mimeType,
                    file.getSize(),
                    imageInfo.width(),
                    imageInfo.height(),
                    sha256(target)
            );
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REVIEW_IMAGE_STORAGE_FAILED",
                    "Failed to store review image"
            );
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REVIEW_IMAGE_EMPTY",
                    "Review image cannot be empty"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REVIEW_IMAGE_TOO_LARGE",
                    "Each review image must be 10MB or less"
            );
        }

        if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REVIEW_IMAGE_TYPE_INVALID",
                    "Only JPEG, PNG, and WebP images are supported"
            );
        }
    }

    private ImageInfo readImageInfo(Path imagePath, String mimeType) {
        if ("image/webp".equals(mimeType)) {
            return new ImageInfo(null, null);
        }

        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                throw new IllegalArgumentException("Invalid image file");
            }
            return new ImageInfo(image.getWidth(), image.getHeight());
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REVIEW_IMAGE_INVALID",
                    "Image file is invalid or corrupted"
            );
        }
    }

    private Path createThumbnail(
            Path source,
            Path targetDir,
            String fileName,
            String mimeType
    ) {
        if ("image/webp".equals(mimeType)) {
            return source;
        }

        try {
            BufferedImage original = ImageIO.read(source.toFile());
            int width = original.getWidth();
            int height = original.getHeight();
            int targetWidth = Math.min(width, 320);
            int targetHeight = Math.max(1, height * targetWidth / width);

            BufferedImage thumbnail = new BufferedImage(
                    targetWidth,
                    targetHeight,
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D graphics = thumbnail.createGraphics();
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
            graphics.dispose();

            String thumbnailName = "thumb-" + fileName.replaceAll("\\.[^.]+$", ".jpg");
            Path thumbnailPath = targetDir.resolve(thumbnailName).normalize();
            ImageIO.write(thumbnail, "jpg", thumbnailPath.toFile());
            return thumbnailPath;
        } catch (Exception exception) {
            return source;
        }
    }

    private String sha256(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record ImageInfo(Integer width, Integer height) {
    }
}
