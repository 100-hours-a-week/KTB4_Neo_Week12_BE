package com.ktb.community.domain.upload.dto;

import lombok.Getter;

@Getter
public class ImageUploadResponseDto {

    private final String imageUrl;

    public ImageUploadResponseDto(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
