package com.ktb.community.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequestDto {

    @NotBlank(message = "닉네임을 입력해주세요.")
    private String nickname;

    private String profileImage;
}
