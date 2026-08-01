package com.ktb.community.domain.upload.service;

import com.ktb.community.domain.upload.dto.ImageUploadCompleteResponseDto;
import com.ktb.community.domain.upload.dto.PresignedUploadRequestDto;
import com.ktb.community.domain.upload.dto.PresignedUploadResponseDto;
import com.ktb.community.domain.upload.model.UploadPurpose;
import com.ktb.community.domain.upload.repository.UploadSession;
import com.ktb.community.domain.upload.repository.UploadSessionRepository;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Map<String, ImageType> ALLOWED_IMAGE_TYPES = Map.of(
            "JPEG", new ImageType("image/jpeg", ".jpg"),
            "PNG", new ImageType("image/png", ".png"),
            "GIF", new ImageType("image/gif", ".gif")
    );
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final UploadSessionRepository uploadSessionRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    @Value("${aws.s3.public-base-url}")
    private String publicBaseUrl;
    @Value("${aws.s3.presigned-url-expiration-seconds}")
    private long presignedUrlExpirationSeconds;
    @Value("${aws.s3.max-image-size}")
    private long maxImageSize;

    public PresignedUploadResponseDto createPresignedUpload(
            PresignedUploadRequestDto request,
            String owner,
            boolean signupUpload
    ) {
        validateRequest(request, signupUpload);

        String uploadId = UUID.randomUUID().toString();
        String temporaryKey = temporaryKey(request.purpose(), owner, uploadId);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(temporaryKey)
                .contentType(request.contentType())
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(presignedUrlExpirationSeconds))
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        uploadSessionRepository.save(new UploadSession(
                uploadId,
                request.purpose(),
                owner,
                temporaryKey,
                request.contentType(),
                request.fileSize(),
                "PENDING",
                null
        ));

        return new PresignedUploadResponseDto(
                uploadId,
                presigned.url().toString(),
                request.contentType(),
                LocalDateTime.now().plusSeconds(presignedUrlExpirationSeconds)
        );
    }

    public ImageUploadCompleteResponseDto completeUpload(
            String uploadId,
            String owner,
            boolean signupUpload
    ) {
        UploadSession session = getSession(uploadId);
        validateOwnerAndPurpose(session, owner, signupUpload);
        if (session.isVerified()) {
            return new ImageUploadCompleteResponseDto(session.finalImageUrl(), session.uploadId());
        }
        if (!uploadSessionRepository.changeStatus(uploadId, "PENDING", "PROCESSING")) {
            UploadSession current = getSession(uploadId);
            if (current.isVerified()) {
                return new ImageUploadCompleteResponseDto(current.finalImageUrl(), current.uploadId());
            }
            throw new ApiException(ErrorCode.CONFLICTED_STATE);
        }

        Path temporaryFile = null;
        try {
            HeadObjectResponse metadata = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(session.temporaryKey())
                    .build());
            if (metadata.contentLength() <= 0
                    || metadata.contentLength() > maxImageSize
                    || metadata.contentLength() != session.declaredFileSize()) {
                deleteObject(session.temporaryKey());
                throw new ApiException(ErrorCode.INVALID_INPUT);
            }

            temporaryFile = Files.createTempFile("community-image-", ".upload");
            try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(session.temporaryKey())
                            .build())) {
                Files.copy(input, temporaryFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            ImageType verifiedType = detectImageType(temporaryFile);
            if (!verifiedType.contentType().equalsIgnoreCase(session.declaredContentType())) {
                deleteObject(session.temporaryKey());
                throw new ApiException(ErrorCode.INVALID_INPUT);
            }

            String finalKey = finalKey(session.purpose(), verifiedType.extension());
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(finalKey)
                            .contentType(verifiedType.contentType())
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromFile(temporaryFile));
            deleteObject(session.temporaryKey());

            String finalImageUrl = stripTrailingSlash(publicBaseUrl) + "/" + finalKey;
            uploadSessionRepository.markVerified(uploadId, finalImageUrl);
            return new ImageUploadCompleteResponseDto(finalImageUrl, uploadId);
        } catch (ApiException error) {
            uploadSessionRepository.delete(uploadId);
            throw error;
        } catch (Exception error) {
            uploadSessionRepository.changeStatus(uploadId, "PROCESSING", "PENDING");
            throw new IllegalStateException("image_upload_failed", error);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public String reserveVerifiedSignupImage(String uploadToken) {
        UploadSession session = getSession(uploadToken);
        if (session.purpose() != UploadPurpose.SIGNUP_PROFILE || !session.isVerified()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        if (!uploadSessionRepository.changeStatus(uploadToken, "VERIFIED", "CONSUMING")) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        return session.finalImageUrl();
    }

    public void consumeSignupImage(String uploadToken) {
        uploadSessionRepository.delete(uploadToken);
    }

    public void releaseSignupImage(String uploadToken) {
        uploadSessionRepository.changeStatus(uploadToken, "CONSUMING", "VERIFIED");
    }

    private void validateRequest(PresignedUploadRequestDto request, boolean signupUpload) {
        if (!ALLOWED_CONTENT_TYPES.contains(request.contentType())
                || request.fileSize() <= 0
                || request.fileSize() > maxImageSize) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        boolean signupPurpose = request.purpose() == UploadPurpose.SIGNUP_PROFILE;
        if (signupUpload != signupPurpose) {
            throw new ApiException(ErrorCode.DENIED_ACCESS);
        }
    }

    private void validateOwnerAndPurpose(UploadSession session, String owner, boolean signupUpload) {
        boolean signupPurpose = session.purpose() == UploadPurpose.SIGNUP_PROFILE;
        if (signupUpload != signupPurpose) throw new ApiException(ErrorCode.DENIED_ACCESS);
        if (!signupUpload && (owner == null || !owner.equals(session.owner()))) {
            throw new ApiException(ErrorCode.DENIED_ACCESS);
        }
    }

    private String temporaryKey(UploadPurpose purpose, String owner, String uploadId) {
        return switch (purpose) {
            case SIGNUP_PROFILE -> "quarantine/signup/" + uploadId;
            case PROFILE -> "quarantine/profile/" + safeOwner(owner) + "/" + uploadId;
            case POST -> "quarantine/post/" + safeOwner(owner) + "/" + uploadId;
        };
    }

    private String finalKey(UploadPurpose purpose, String extension) {
        String directory = purpose == UploadPurpose.POST ? "post" : "profile";
        return "public/" + directory + "/" + UUID.randomUUID() + extension;
    }

    private ImageType detectImageType(Path file) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.toFile())) {
            if (imageInput == null) throw new ApiException(ErrorCode.INVALID_INPUT);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new ApiException(ErrorCode.INVALID_INPUT);
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                    throw new ApiException(ErrorCode.INVALID_INPUT);
                }
                ImageType type = ALLOWED_IMAGE_TYPES.get(reader.getFormatName().toUpperCase(Locale.ROOT));
                if (type == null) throw new ApiException(ErrorCode.INVALID_INPUT);
                return type;
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    private UploadSession getSession(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) throw new ApiException(ErrorCode.INVALID_INPUT);
        return uploadSessionRepository.findById(uploadId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INPUT));
    }

    private void deleteObject(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    private String safeOwner(String owner) {
        if (owner == null || owner.isBlank()) throw new ApiException(ErrorCode.UNAUTHORIZED_USER);
        return owner.replaceAll("[^a-zA-Z0-9@._-]", "_");
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record ImageType(String contentType, String extension) {
    }
}
