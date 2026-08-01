package com.ktb.community.domain.upload.dto;

import com.ktb.community.domain.upload.model.UploadPurpose;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PresignedUploadRequestDto(
        @NotNull UploadPurpose purpose,
        @NotNull String contentType,
        @Positive long fileSize
) {
}
