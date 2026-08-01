package com.ktb.community.domain.upload.dto;

public record ImageUploadCompleteResponseDto(
        String imageUrl,
        String uploadToken
) {
}
