package com.ktb.community.domain.upload.service;

import com.ktb.community.domain.upload.dto.ImageUploadResponseDto;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageUploadService {

    private static final Path UPLOAD_ROOT = Paths.get("uploads", "images").toAbsolutePath().normalize();
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    public ImageUploadResponseDto uploadImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }

        try {
            Files.createDirectories(UPLOAD_ROOT);

            String extension = getExtension(image.getOriginalFilename(), contentType);
            String storedFileName = UUID.randomUUID() + extension;
            Path targetPath = UPLOAD_ROOT.resolve(storedFileName).normalize();

            image.transferTo(targetPath);

            return new ImageUploadResponseDto("/uploads/images/" + storedFileName);
        } catch (IOException error) {
            throw new IllegalStateException("image_upload_failed", error);
        }
    }

    private String getExtension(String originalFileName, String contentType) {
        if (originalFileName != null) {
            int extensionIndex = originalFileName.lastIndexOf(".");
            if (extensionIndex >= 0 && extensionIndex < originalFileName.length() - 1) {
                return originalFileName.substring(extensionIndex).toLowerCase();
            }
        }

        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
