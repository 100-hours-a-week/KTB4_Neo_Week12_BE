package com.ktb.community.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class LoginResponseDto {
    private Long userId;
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
}
