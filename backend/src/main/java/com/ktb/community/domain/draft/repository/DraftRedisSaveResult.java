package com.ktb.community.domain.draft.repository;

import java.util.Objects;

public record DraftRedisSaveResult(
        DraftRedisSaveStatus status,
        DraftCache cache
) {

    public DraftRedisSaveResult {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(cache, "cache must not be null");
    }

    public boolean isSuccess() {
        return status == DraftRedisSaveStatus.SAVED
                || status == DraftRedisSaveStatus.IDEMPOTENT;
    }

}
