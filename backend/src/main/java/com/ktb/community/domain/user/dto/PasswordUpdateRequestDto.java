package com.ktb.community.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PasswordUpdateRequestDto {

    @NotBlank(message = "기존 비밀번호를 입력하세요.")
    private String curPassword;

    @NotBlank(message = "새 비밀번호를 입력하세요.")
    private String password;

    @NotBlank(message = "새 비밀번호 한번 더 입력하세요.")
    private String passwordCheck;
}
