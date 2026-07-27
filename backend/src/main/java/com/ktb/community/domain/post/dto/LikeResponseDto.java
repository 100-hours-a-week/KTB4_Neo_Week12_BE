package com.ktb.community.domain.post.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class LikeResponseDto {

    private Long postId;
    private boolean isLiked;
    private int likes;
}
