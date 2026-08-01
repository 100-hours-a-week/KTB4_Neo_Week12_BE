package com.ktb.community.domain.draft.dto;

import com.ktb.community.domain.draft.entity.DraftStatus;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DraftResponseDto {

    private Long draftId;
    private String title;
    private String postBody;
    private String postImage;
    private DraftStatus status;
    private long contentVersion;
    private LocalDateTime updatedAt;
    private LocalDateTime rdbSavedAt;
}
