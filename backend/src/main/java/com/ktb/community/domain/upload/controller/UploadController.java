package com.ktb.community.domain.upload.controller;

import com.ktb.community.global.common.ApiResponse;
import com.ktb.community.domain.upload.dto.ImageUploadResponseDto;
import com.ktb.community.domain.upload.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final ImageUploadService imageUploadService;

    @PostMapping("/images")
    public ResponseEntity<ApiResponse<ImageUploadResponseDto>> uploadImage(
            @RequestParam("image") MultipartFile image
    ) {
        ImageUploadResponseDto response = imageUploadService.uploadImage(image);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>("upload_image_success", response));
    }
}
