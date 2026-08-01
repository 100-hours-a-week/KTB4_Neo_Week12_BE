package com.ktb.community.domain.draft.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DraftPublishResponseDto {

    private Long postId;
    private String title;
    private String postBody;
    private String postImage;
    private Long userId;
    private String nickname;
    private String profileImage;
    private LocalDateTime createdAt;
}
