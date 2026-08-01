package com.ktb.community.domain.upload.controller;

import com.ktb.community.domain.upload.dto.ImageUploadCompleteResponseDto;
import com.ktb.community.domain.upload.dto.PresignedUploadRequestDto;
import com.ktb.community.domain.upload.dto.PresignedUploadResponseDto;
import com.ktb.community.domain.upload.service.ImageUploadService;
import com.ktb.community.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/signup-images")
@RequiredArgsConstructor
public class SignupImageUploadController {

    private final ImageUploadService imageUploadService;

    @PostMapping("/presign")
    public ResponseEntity<ApiResponse<PresignedUploadResponseDto>> createPresignedUpload(
            @Valid @RequestBody PresignedUploadRequestDto request
    ) {
        PresignedUploadResponseDto response = imageUploadService.createPresignedUpload(
                request, null, true
        );
        return ResponseEntity.ok(new ApiResponse<>("create_presigned_upload_success", response));
    }

    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<ApiResponse<ImageUploadCompleteResponseDto>> completeUpload(
            @PathVariable String uploadId
    ) {
        ImageUploadCompleteResponseDto response = imageUploadService.completeUpload(uploadId, null, true);
        return ResponseEntity.ok(new ApiResponse<>("complete_image_upload_success", response));
    }
}
