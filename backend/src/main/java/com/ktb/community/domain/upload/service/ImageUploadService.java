package com.ktb.community.domain.upload.service;

import com.ktb.community.domain.upload.dto.ImageUploadResponseDto;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageUploadService {

    private static final Path UPLOAD_ROOT =
            Paths.get("uploads", "images")
                    .toAbsolutePath()
                    .normalize();

    private static final Map<String, ImageType> ALLOWED_IMAGE_TYPES = Map.of(
            "JPEG", new ImageType("image/jpeg", ".jpg"),
            "PNG", new ImageType("image/png", ".png"),
            "GIF", new ImageType("image/gif", ".gif")
    );

    public ImageUploadResponseDto uploadImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }

        try {
            byte[] fileBytes = image.getBytes();

            // 실제 파일 바이트에서 이미지 형식을 판별한다.
            ImageType verifiedType = detectImageType(fileBytes);

            // 클라이언트가 선언한 Content-Type과 실제 형식도 비교한다.
            String declaredContentType = image.getContentType();

            if (declaredContentType == null ||
                    !verifiedType.contentType()
                            .equalsIgnoreCase(declaredContentType)) {
                throw new ApiException(ErrorCode.INVALID_INPUT);
            }

            Files.createDirectories(UPLOAD_ROOT);

            // 원본 파일명은 사용하지 않는다.
            String storedFileName =
                    UUID.randomUUID() + verifiedType.extension();

            Path targetPath =
                    UPLOAD_ROOT.resolve(storedFileName).normalize();

            if (!targetPath.startsWith(UPLOAD_ROOT)) {
                throw new ApiException(ErrorCode.INVALID_INPUT);
            }

            Files.write(
                    targetPath,
                    fileBytes,
                    StandardOpenOption.CREATE_NEW
            );

            return new ImageUploadResponseDto(
                    "/uploads/images/" + storedFileName
            );
        } catch (ApiException error) {
            throw error;
        } catch (IOException error) {
            throw new IllegalStateException(
                    "image_upload_failed",
                    error
            );
        }
    }

    private ImageType detectImageType(byte[] fileBytes) {
        try (
                ByteArrayInputStream input =
                        new ByteArrayInputStream(fileBytes);
                ImageInputStream imageInput =
                        ImageIO.createImageInputStream(input)
        ) {
            if (imageInput == null) {
                throw new ApiException(ErrorCode.INVALID_INPUT);
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(imageInput);

            if (!readers.hasNext()) {
                // 실제 이미지 형식으로 인식되지 않음
                throw new ApiException(ErrorCode.INVALID_INPUT);
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInput, true, true);

                // 이미지 헤더와 크기를 실제로 읽을 수 있는지 확인
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0) {
                    throw new ApiException(ErrorCode.INVALID_INPUT);
                }

                String formatName =
                        reader.getFormatName()
                                .toUpperCase(Locale.ROOT);

                ImageType imageType =
                        ALLOWED_IMAGE_TYPES.get(formatName);

                if (imageType == null) {
                    throw new ApiException(ErrorCode.INVALID_INPUT);
                }

                return imageType;
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    private record ImageType(
            String contentType,
            String extension
    ) {
    }
}