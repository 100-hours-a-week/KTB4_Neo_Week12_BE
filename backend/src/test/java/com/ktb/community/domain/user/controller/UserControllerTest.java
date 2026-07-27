package com.ktb.community.domain.user.controller;

import com.ktb.community.domain.user.dto.LoginResponseDto;
import com.ktb.community.domain.user.dto.UserResponseDto;
import com.ktb.community.domain.user.dto.RefreshTokenResponseDto;
import com.ktb.community.domain.user.dto.SignUpResponseDto;
import com.ktb.community.domain.user.service.UserService;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import com.ktb.community.global.exception.GlobalExceptionHandler;
import com.ktb.community.security.config.SecurityConfig;
import com.ktb.community.security.filter.JwtAuthenticationFilter;
import com.ktb.community.security.handler.CustomAuthenticationEntryPoint;
import com.ktb.community.security.handler.JwtAccessDeniedHandler;
import com.ktb.community.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        CustomAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserController userController;

    @Test
    @DisplayName("회원가입 성공 시 201과 userId를 응답한다")
    void signup_success() throws Exception {
        // given
        given(userService.signup(any()))
                .willReturn(new SignUpResponseDto(1L));

        // when & then
        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "Test1234!",
                                  "passwordCheck": "Test1234!",
                                  "nickname": "neo",
                                  "profileImage": "/profile.png"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("register_success"))
                .andExpect(jsonPath("$.data.userId").value(1));

        verify(userService).signup(any());
    }

    @Test
    @DisplayName("회원가입 요청값이 올바르지 않으면 400 invalid_input을 응답한다")
    void signup_invalidInput_returnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(post("/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "",
                                  "passwordCheck": "",
                                  "nickname": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("invalid_input"));

        verify(userService, never()).signup(any());
    }

    @Test
    @DisplayName("로그인 성공 시 accessToken과 refreshToken 쿠키를 응답한다")
    void login_success_returnsAccessTokenAndRefreshTokenCookie() throws Exception {
        // given
        given(userService.login(any()))
                .willReturn(new LoginResponseDto(1L, "access-token", "refresh-token"));

        // when & then
        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "Test1234!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("login_success"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

        verify(userService).login(any());
    }

    @Test
    @DisplayName("accessToken 재발급 성공 시 새 accessToken을 응답한다")
    void refresh_success_returnsNewAccessToken() throws Exception {
        // given
        given(userService.refreshAccessToken("refresh-token"))
                .willReturn(new RefreshTokenResponseDto("new-access-token"));

        // when & then
        mockMvc.perform(post("/users/refresh")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("refresh_token_success"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));

        verify(userService).refreshAccessToken("refresh-token");
    }

    @Test
    @DisplayName("accessToken 재발급 실패 시 refreshToken 쿠키를 삭제한다")
    void refresh_fail_deletesRefreshTokenCookie() throws Exception {
        // given
        given(userService.refreshAccessToken(null))
                .willThrow(new ApiException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // when & then
        mockMvc.perform(post("/users/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("refresh_token_not_found"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")));

        verify(userService).refreshAccessToken(null);
    }

    @Test
    @DisplayName("로그아웃 성공 시 204와 refreshToken 삭제 쿠키를 응답한다")
    void logout_success_deletesRefreshTokenCookie() {
        // when
        ResponseEntity<Void> response = userController.logout(userDetails());

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("refreshToken=", "Max-Age=0", "HttpOnly", "Path=/");
        verify(userService).logout("test@example.com");
    }

    @Test
    @DisplayName("마이페이지 조회 성공 시 유저 정보를 응답한다")
    void getMyPage_success_returnsUser() throws Exception {
        // given
        given(userService.getMyPage("test@example.com", 1L))
                .willReturn(new UserResponseDto(1L, "neo", "test@example.com", "/profile.png"));

        // when & then
        mockMvc.perform(get("/users/1")
                        .with(user("test@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("get_mypage_success"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.nickname").value("neo"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.profileImage").value("/profile.png"));

        verify(userService).getMyPage("test@example.com", 1L);
    }

    @Test
    @DisplayName("인증 없이 마이페이지를 조회하면 401을 응답한다")
    void getMyPage_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("unauthorized_user"));

        verify(userService, never()).getMyPage(any(), any());
    }

    @Test
    @DisplayName("권한이 부족하면 403을 응답한다")
    void getMyPage_withoutRequiredRole_returnsForbidden() throws Exception {
        mockMvc.perform(get("/users/1")
                        .with(user("test@example.com").roles("GUEST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("denied_access"));

        verify(userService, never()).getMyPage(any(), any());
    }

    @Test
    @DisplayName("회원 정보 수정 성공 시 성공 메시지를 응답한다")
    void updateUser_success_returnsOk() {
        // when
        ResponseEntity<?> response = userController.updateUser(userDetails(), 1L, null);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userService).updateUser("test@example.com", 1L, null);
    }

    @Test
    @DisplayName("비밀번호 수정 성공 시 성공 메시지를 응답한다")
    void updatePassword_success_returnsOk() {
        // when
        ResponseEntity<?> response = userController.updatePassword(userDetails(), 1L, null);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(userService).updatePassword("test@example.com", 1L, null);
    }

    @Test
    @DisplayName("회원 탈퇴 성공 시 true를 응답한다")
    void deleteUser_success_returnsTrue() {
        // when
        ResponseEntity<?> response = userController.deleteUser(userDetails(), 1L);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("refreshToken=", "Max-Age=0", "HttpOnly", "Path=/");
        verify(userService).deleteUser("test@example.com", 1L);
    }

    private UserDetails userDetails() {
        return org.springframework.security.core.userdetails.User
                .withUsername("test@example.com")
                .password("password")
                .roles("USER")
                .build();
    }

}
