package com.ktb.community.domain.upload.dto;

import java.time.LocalDateTime;

public record PresignedUploadResponseDto(
        String uploadId,
        String uploadUrl,
        String contentType,
        LocalDateTime expiresAt
) {
}
