package com.ktb.community.domain.draft.dto;

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
    private LocalDateTime updatedAt;
}
