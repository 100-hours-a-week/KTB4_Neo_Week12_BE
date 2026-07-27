package com.ktb.community.domain.post.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostCreateResponseDto {

    private Long postId;
    private String title;
    private String postBody;
    private String postImage;

    private Long userId;
    private String nickname;
    private String profileImage;
    private LocalDateTime createdAt;
}
