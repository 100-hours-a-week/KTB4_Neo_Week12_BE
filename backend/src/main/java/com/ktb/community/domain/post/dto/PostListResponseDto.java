package com.ktb.community.domain.post.dto;

import com.ktb.community.domain.post.entity.Post;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PostListResponseDto {

    private Long postId;
    private String title;
    private Long userId;
    private String nickname;
    private String profileImage;
    private String postImage;
    private LocalDateTime createdAt;
    private int likes;
    private int comments;
    private int views;
    private boolean isBlinded;
    private boolean isLiked;
    private boolean isCommented;

    public PostListResponseDto(Post post) {
        this(post, false, false);
    }

    public PostListResponseDto(Post post, boolean isLiked, boolean isCommented) {
        this.postId = post.getPostId();
        this.title = post.isBlinded()
                ? "숨김 처리된 게시글입니다."
                : post.getTitle();
        this.userId = post.getUser().getUserId();
        this.nickname = post.getUser().isDeleted()
                ? "알 수 없음"
                : post.getUser().getNickname();
        this.profileImage = post.getUser().isDeleted()
                ? null
                : post.getUser().getProfileImage();
        this.postImage = post.isBlinded() ? null : post.getPostImage();
        this.createdAt = post.getCreatedAt();
        this.likes = post.getLikes();
        this.comments = post.getComments();
        this.views = post.getViews();
        this.isBlinded = post.isBlinded();
        this.isLiked = isLiked;
        this.isCommented = isCommented;
    }
}
