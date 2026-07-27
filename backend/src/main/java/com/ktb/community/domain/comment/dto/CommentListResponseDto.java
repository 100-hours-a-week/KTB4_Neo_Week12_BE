package com.ktb.community.domain.comment.dto;

import com.ktb.community.domain.comment.entity.Comment;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class CommentListResponseDto {

    private Long commentId;
    private Long userId;
    private String nickname;
    private String profileImage;
    private String commentBody;
    private boolean isDeleted;
    private LocalDateTime createdAt;
    private List<ReplyResponseDto> replies;

    public CommentListResponseDto(Comment comment, List<ReplyResponseDto> replies) {

        this.commentId = comment.getCommentId();
        this.userId = comment.getUser().getUserId();
        this.nickname = comment.isDeleted() || comment.getUser().isDeleted()
                ? "알 수 없음"
                : comment.getUser().getNickname();
        this.profileImage = comment.isDeleted() || comment.getUser().isDeleted()
                ? null
                : comment.getUser().getProfileImage();
        this.commentBody = comment.isDeleted() ? "삭제된 댓글입니다." : comment.getCommentBody();
        this.isDeleted = comment.isDeleted();
        this.createdAt = comment.getCreatedAt();
        this.replies = replies;
    }

    @Getter
    @AllArgsConstructor
    public static class ReplyResponseDto {

        private Long commentId;
        private Long userId;
        private String nickname;
        private String profileImage;
        private String commentBody;
        private boolean isDeleted;
        private LocalDateTime createdAt;
    }
}
