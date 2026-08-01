package com.ktb.community.domain.draft.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DraftAutosaveResponseDto {

    private Long draftId;
    private long contentVersion;
    private LocalDateTime updatedAt;
}
