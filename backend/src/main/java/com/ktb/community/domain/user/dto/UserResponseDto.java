package com.ktb.community.domain.user.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class UserResponseDto {

    private Long userId;
    private String nickname;
    private String email;
    private String profileImage;
}
