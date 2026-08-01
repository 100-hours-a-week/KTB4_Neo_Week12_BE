package com.ktb.community.domain.draft.service;

import com.ktb.community.domain.draft.dto.DraftResponseDto;

public record DraftCreateResult(
        DraftResponseDto draft,
        boolean created
) {
}
