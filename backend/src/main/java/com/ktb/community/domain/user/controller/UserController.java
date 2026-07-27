package com.ktb.community.domain.user.controller;

import com.ktb.community.global.common.ApiResponse;
import com.ktb.community.domain.user.dto.LoginRequestDto;
import com.ktb.community.domain.user.dto.LoginResponseDto;
import com.ktb.community.domain.user.dto.PasswordUpdateRequestDto;
import com.ktb.community.domain.user.dto.SignUpRequestDto;
import com.ktb.community.domain.user.dto.SignUpResponseDto;
import com.ktb.community.domain.user.dto.UserResponseDto;
import com.ktb.community.domain.user.dto.UserUpdateRequestDto;
import com.ktb.community.domain.user.dto.RefreshTokenResponseDto;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private static final Duration REFRESH_TOKEN_COOKIE_MAX_AGE = Duration.ofDays(7);

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponseDto>> signup(@Valid @RequestBody SignUpRequestDto request) {

        SignUpResponseDto response = userService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>("register_success", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto request) {

        LoginResponseDto response = userService.login(request);
        ResponseCookie refreshTokenCookie = createRefreshTokenCookie(response.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(new ApiResponse<>("login_success", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        userService.logout(userDetails.getUsername());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie().toString())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponseDto>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        try {
            RefreshTokenResponseDto response = userService.refreshAccessToken(refreshToken);

            return ResponseEntity.ok(
                    new ApiResponse<>("refresh_token_success", response)
            );
        } catch (ApiException e) {
            return ResponseEntity
                    .status(e.getStatus())
                    .header(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie().toString())
                    .body(new ApiResponse<>(e.getMessage(), null));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponseDto>> getMyPage(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId
    ) {
        UserResponseDto response = userService.getMyPage(userDetails.getUsername(), userId);

        return ResponseEntity.ok(
                new ApiResponse<>("get_mypage_success", response)
        );
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateRequestDto request
    )
    {
        userService.updateUser(userDetails.getUsername(), userId, request);

        return ResponseEntity.ok(
                new ApiResponse<>("update_user_success", null)
        );
    }

    @PatchMapping("/{userId}/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId,
            @Valid @RequestBody PasswordUpdateRequestDto request
    ) {
        userService.updatePassword(userDetails.getUsername(), userId, request);

        return ResponseEntity.ok(
                new ApiResponse<>("update_password_success", null)
        );
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Boolean>> deleteUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long userId
    ) {
        userService.deleteUser(userDetails.getUsername(), userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie().toString())
                .body(new ApiResponse<>("delete_user_success", true));
    }

    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")    // CSRF 공격을 줄이기 위해, 쿠키가 cross-site 요청에 실릴 수 있는 범위를 제한하는 속성
                .path("/")
                .maxAge(REFRESH_TOKEN_COOKIE_MAX_AGE)
                .build();
    }

    private ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }
}
