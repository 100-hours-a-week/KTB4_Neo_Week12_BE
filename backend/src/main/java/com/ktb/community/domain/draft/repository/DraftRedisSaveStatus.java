package com.ktb.community.domain.draft.repository;

public enum DraftRedisSaveStatus {
    SAVED,
    IDEMPOTENT,
    VERSION_CONFLICT,
    CONTENT_CONFLICT
}
