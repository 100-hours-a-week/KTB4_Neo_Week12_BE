package com.ktb.community.domain.upload.repository;

import com.ktb.community.domain.upload.model.UploadPurpose;

public record UploadSession(
        String uploadId,
        UploadPurpose purpose,
        String owner,
        String temporaryKey,
        String declaredContentType,
        long declaredFileSize,
        String status,
        String finalImageUrl
) {
    public boolean isVerified() {
        return "VERIFIED".equals(status) && finalImageUrl != null;
    }
}
