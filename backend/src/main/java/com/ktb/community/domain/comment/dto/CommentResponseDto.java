package com.ktb.community.domain.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CommentResponseDto {

    private Long commentId;
    private Long parentCommentId;
    private Long userId;
    private String nickname;
    private String profileImage;
    private String commentBody;
    private LocalDateTime createdAt;
}
