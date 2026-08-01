package com.ktb.community.domain.draft.repository;

import static com.ktb.community.domain.draft.support.DraftContentNormalizer.normalizeImage;
import static com.ktb.community.domain.draft.support.DraftContentNormalizer.normalizeText;

import java.time.LocalDateTime;
import java.util.Objects;

public record DraftCache(
        Long draftId,
        String title,
        String postBody,
        String postImage,
        long contentVersion,
        LocalDateTime updatedAt
) {

    public DraftCache {
        Objects.requireNonNull(draftId, "draftId must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (contentVersion < 1) {
            throw new IllegalArgumentException("contentVersion must be at least 1");
        }

        title = normalizeText(title);
        postBody = normalizeText(postBody);
        postImage = normalizeImage(postImage);
    }

    public boolean hasSameContent(DraftCache other) {
        if (other == null) return false;

        return Objects.equals(title, other.title())
                && Objects.equals(postBody, other.postBody())
                && Objects.equals(postImage, other.postImage());
    }

}
