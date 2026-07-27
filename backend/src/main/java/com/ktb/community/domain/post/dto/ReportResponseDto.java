package com.ktb.community.domain.post.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class ReportResponseDto {

    private Long postId;
    private int reportCount;
    private boolean isBlinded;
}
