package com.ktb.community.domain.user.dto;

import lombok.Getter;

@Getter
public class SignUpResponseDto {

    private Long userId;

    public SignUpResponseDto(Long userId) {
        this.userId = userId;
    }

}
