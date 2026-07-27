package com.ktb.community.domain.user.service;

import com.ktb.community.domain.user.dto.LoginRequestDto;
import com.ktb.community.domain.user.dto.LoginResponseDto;
import com.ktb.community.domain.user.dto.PasswordUpdateRequestDto;
import com.ktb.community.domain.user.dto.RefreshTokenResponseDto;
import com.ktb.community.domain.user.dto.SignUpRequestDto;
import com.ktb.community.domain.user.dto.SignUpResponseDto;
import com.ktb.community.domain.user.dto.UserResponseDto;
import com.ktb.community.domain.user.dto.UserUpdateRequestDto;
import com.ktb.community.domain.user.entity.RefreshToken;
import com.ktb.community.domain.user.entity.User;
import com.ktb.community.domain.user.repository.RefreshTokenRepository;
import com.ktb.community.domain.user.repository.UserRepository;
import com.ktb.community.global.exception.ApiException;
import com.ktb.community.global.exception.ErrorCode;
import com.ktb.community.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserService userService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = user(
                1L,
                "test@example.com",
                "encoded-password",
                "neo",
                "/profile.png"
        );
    }

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("회원가입 성공 시 비밀번호를 암호화하고 유저를 저장한다")
        void signup_success() {
            // given
            SignUpRequestDto request = signUpRequest(
                    "test@example.com",
                    "Password123!",
                    "Password123!",
                    "neo",
                    "/profile.png"
            );

            given(userRepository.existsByEmailAndDeletedFalse("test@example.com")).willReturn(false);
            given(userRepository.existsByNicknameAndDeletedFalse("neo")).willReturn(false);
            given(passwordEncoder.encode("Password123!")).willReturn("encoded-password");
            given(userRepository.save(any(User.class))).willAnswer(invocation -> {
                User savedUser = invocation.getArgument(0); // save 메소드에 전달된 첫 인자를 저장하기 위해서 꺼낸다.
                ReflectionTestUtils.setField(savedUser, "userId", 1L);
                return savedUser;
            });

            // when
            SignUpResponseDto response = userService.signup(request);

            // then
            assertThat(response.getUserId()).isEqualTo(1L);

            verify(userRepository).save(argThat(user ->
                    user.getEmail().equals("test@example.com")
                            && user.getPassword().equals("encoded-password")
                            && user.getNickname().equals("neo")
                            && user.getProfileImage().equals("/profile.png")
                            && !user.isDeleted()
            ));
        }
        
        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "이메일이 중복되면 회원가입에 실패한다, true, false",
                "닉네임이 중복되면 회원가입에 실패한다, false, true"
        })
        void signup_duplicateEmailOrNickname_throwsAlreadyExists(
                String displayName,
                boolean duplicatedEmail,
                boolean duplicatedNickname
        ) {
            // given
            SignUpRequestDto request = signUpRequest(
                    "test@example.com",
                    "Password123!",
                    "Password123!",
                    "neo",
                    null
            );

            given(userRepository.existsByEmailAndDeletedFalse("test@example.com"))
                    .willReturn(duplicatedEmail);

            if (!duplicatedEmail) {
                given(userRepository.existsByNicknameAndDeletedFalse("neo"))
                        .willReturn(duplicatedNickname);
            }

            // when & then
            assertThatThrownBy(() -> userService.signup(request))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("비밀번호 확인이 일치하지 않으면 회원가입에 실패한다")
        void signup_passwordMismatch_throwsPasswordMismatch() {
            // given
            SignUpRequestDto request = signUpRequest(
                    "test@example.com",
                    "Password123!",
                    "Different123!",
                    "neo",
                    null
            );

            given(userRepository.existsByEmailAndDeletedFalse("test@example.com")).willReturn(false);
            given(userRepository.existsByNicknameAndDeletedFalse("neo")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.signup(request))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("로그인 성공 시 accessToken과 refreshToken을 발급하고 refreshToken을 저장한다")
        void login_success() {
            LoginRequestDto request =
                    loginRequest("test@example.com", "Password123!");

            given(userRepository.findByEmailAndDeletedFalse("test@example.com"))
                    .willReturn(Optional.of(activeUser));
            given(passwordEncoder.matches("Password123!", "encoded-password"))
                    .willReturn(true);
            given(jwtTokenProvider.createAccessToken(activeUser))
                    .willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(activeUser))
                    .willReturn("refresh-token");
            given(refreshTokenRepository.findByUser(activeUser))
                    .willReturn(Optional.empty());

            LoginResponseDto response = userService.login(request);

            assertThat(response.getUserId()).isEqualTo(1L);

            verify(refreshTokenRepository).save(argThat(refreshToken ->
                    refreshToken.getUser().equals(activeUser)
                            && refreshToken.getToken().equals("refresh-token")
                            && !refreshToken.isExpired()
            ));
        }

        @Test
        @DisplayName("존재하지 않는 이메일이면 로그인에 실패한다")
        void login_emailNotFound_throwsUserNotFound() {
            // given
            LoginRequestDto request = loginRequest("test@example.com", "Password123!");

            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtTokenProvider, never()).createAccessToken(any());
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패한다")
        void login_wrongPassword_throwsUserNotFound() {
            // given
            LoginRequestDto request = loginRequest("test@example.com", "WrongPassword!");
            User user = user(1L, "test@example.com", "encoded-password", "neo", "/profile.png");

            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("WrongPassword!", "encoded-password")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            verify(jwtTokenProvider, never()).createAccessToken(any());
            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("기존 refreshToken이 있으면 새로 저장하지 않고 갱신한다")
        void login_existingRefreshToken_updatesToken() {
            // given
            LoginRequestDto request = loginRequest("test@example.com", "Password123!");
            User user = user(1L, "test@example.com", "encoded-password", "neo", "/profile.png");
            RefreshToken existingRefreshToken = new RefreshToken(
                    user,
                    "old-refresh-token",
                    LocalDateTime.now().plusDays(7)
            );

            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("Password123!", "encoded-password")).willReturn(true);
            given(jwtTokenProvider.createAccessToken(user)).willReturn("access-token");
            given(jwtTokenProvider.createRefreshToken(user)).willReturn("new-refresh-token");
            given(refreshTokenRepository.findByUser(user)).willReturn(Optional.of(existingRefreshToken));

            // when
            LoginResponseDto response = userService.login(request);

            // then
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
            assertThat(existingRefreshToken.getToken()).isEqualTo("new-refresh-token");
            assertThat(existingRefreshToken.isExpired()).isFalse();

            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class DeleteUser {

        @Test
        @DisplayName("회원 탈퇴 성공 시 유저를 soft delete 하고 프로필 이미지를 제거한다")
        void deleteUser_success() {
            given(userRepository.findByEmailAndDeletedFalse("test@example.com"))
                    .willReturn(Optional.of(activeUser));

            userService.deleteUser("test@example.com", 1L);

            assertThat(activeUser.isDeleted()).isTrue();
            assertThat(activeUser.getProfileImage()).isNull();
            assertThat(activeUser.getDeletedAt()).isNotNull();

            verify(refreshTokenRepository).deleteByUser(activeUser);
        }

        @Test
        @DisplayName("본인이 아니면 회원 탈퇴에 실패한다")
        void deleteUser_notOwner_throwsDeniedAccess() {
            // given
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(activeUser));

            // when & then
            assertThatThrownBy(() -> userService.deleteUser("test@example.com", 2L))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DENIED_ACCESS);

            assertThat(activeUser.isDeleted()).isFalse();
            verify(refreshTokenRepository, never()).deleteByUser(any());
        }

        @Test
        @DisplayName("이미 삭제된 유저면 회원 탈퇴에 실패한다")
        void deleteUser_deletedUser_throwsUnauthorizedUser() {
            // given
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.deleteUser("test@example.com", 1L))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED_USER);
        }

    }


    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("로그아웃 성공 시 refreshToken을 삭제한다")
        void logout_success() {
            given(userRepository.findByEmailAndDeletedFalse("test@example.com"))
                    .willReturn(Optional.of(activeUser));

            userService.logout("test@example.com");

            verify(refreshTokenRepository).deleteByUser(activeUser);
        }

        @Test
        @DisplayName("존재하지 않는 유저면 로그아웃에 실패한다")
        void logout_userNotFound_throwsUnauthorizedUser() {
            // given
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.logout("test@example.com"))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED_USER);

            verify(refreshTokenRepository, never()).deleteByUser(any());
        }
    }

    @Nested
    @DisplayName("마이페이지 조회")
    class GetMyPage {

        @Test
        @DisplayName("본인 마이페이지 조회에 성공한다")
        void getMyPage_success() {
            // given
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(activeUser));

            // when
            UserResponseDto response = userService.getMyPage("test@example.com", 1L);

            // then
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getEmail()).isEqualTo("test@example.com");
            assertThat(response.getNickname()).isEqualTo("neo");
            assertThat(response.getProfileImage()).isEqualTo("/profile.png");
        }

        @Test
        @DisplayName("본인이 아니면 마이페이지 조회에 실패한다")
        void getMyPage_notOwner_throwsDeniedAccess() {
            // given
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(activeUser));

            // when & then
            assertThatThrownBy(() -> userService.getMyPage("test@example.com", 2L))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DENIED_ACCESS);
        }
    }

    @Nested
    @DisplayName("회원 정보 수정")
    class UpdateUser {

        @Test
        @DisplayName("회원 정보 수정에 성공한다")
        void updateUser_success() {
            // given
            UserUpdateRequestDto request = userUpdateRequest("new-neo", "/new-profile.png");
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(activeUser));

            // when
            userService.updateUser("test@example.com", 1L, request);

            // then
            assertThat(activeUser.getNickname()).isEqualTo("new-neo");
            assertThat(activeUser.getProfileImage()).isEqualTo("/new-profile.png");
        }

        @Test
        @DisplayName("본인이 아니면 회원 정보 수정에 실패한다")
        void updateUser_notOwner_throwsDeniedAccess() {
            // given
            UserUpdateRequestDto request = userUpdateRequest("new-neo", "/new-profile.png");
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(activeUser));

            // when & then
            assertThatThrownBy(() -> userService.updateUser("test@example.com", 2L, request))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.DENIED_ACCESS);

            assertThat(activeUser.getNickname()).isEqualTo("neo");
        }
    }

    @Nested
    @DisplayName("비밀번호 수정")
    class UpdatePassword {

        @Test
        @DisplayName("비밀번호 수정에 성공한다")
        void updatePassword_success() {
            // given
            PasswordUpdateRequestDto request = passwordUpdateRequest("OldPassword123!", "NewPassword123!", "NewPassword123!");
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(activeUser));
            given(passwordEncoder.matches("OldPassword123!", "encoded-password")).willReturn(true);
            given(passwordEncoder.encode("NewPassword123!")).willReturn("new-encoded-password");

            // when
            userService.updatePassword("test@example.com", 1L, request);

            // then
            assertThat(activeUser.getPassword()).isEqualTo("new-encoded-password");
        }

        @Test
        @DisplayName("새 비밀번호 확인이 일치하지 않으면 실패한다")
        void updatePassword_passwordMismatch_throwsPasswordMismatch() {
            // given
            PasswordUpdateRequestDto request = passwordUpdateRequest("OldPassword123!", "NewPassword123!", "Different123!");
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(activeUser));

            // when & then
            assertThatThrownBy(() -> userService.updatePassword("test@example.com", 1L, request))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("현재 비밀번호가 일치하지 않으면 실패한다")
        void updatePassword_wrongCurrentPassword_throwsInvalidPassword() {
            // given
            PasswordUpdateRequestDto request = passwordUpdateRequest("WrongPassword123!", "NewPassword123!", "NewPassword123!");
            given(userRepository.findByEmailAndDeletedFalse("test@example.com")).willReturn(Optional.of(activeUser));
            given(passwordEncoder.matches("WrongPassword123!", "encoded-password")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.updatePassword("test@example.com", 1L, request))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PASSWORD);

            verify(passwordEncoder, never()).encode(any());
        }
    }

    @Nested
    @DisplayName("accessToken 재발급")
    class RefreshAccessToken {

        @Test
        @DisplayName("정상 refreshToken이면 새 accessToken을 반환한다")
        void refreshAccessToken_success() {
            // given
            RefreshToken refreshToken = new RefreshToken(activeUser, "refresh-token", LocalDateTime.now().plusDays(1));

            given(refreshTokenRepository.findByToken("refresh-token")).willReturn(Optional.of(refreshToken));
            given(jwtTokenProvider.createAccessToken(activeUser)).willReturn("new-access-token");

            // when
            RefreshTokenResponseDto response = userService.refreshAccessToken("refresh-token");

            // then
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            verify(jwtTokenProvider).validateRefreshTokenOrThrow("refresh-token");
            verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
        }

        @Test
        @DisplayName("refreshToken이 null이면 실패한다")
        void refreshAccessToken_null_throwsRefreshTokenNotFound() {
            assertThatThrownBy(() -> userService.refreshAccessToken(null))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);

            verify(refreshTokenRepository, never()).findByToken(any());
        }

        @Test
        @DisplayName("refreshToken이 공백이면 실패한다")
        void refreshAccessToken_blank_throwsRefreshTokenNotFound() {
            assertThatThrownBy(() -> userService.refreshAccessToken("  "))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);

            verify(refreshTokenRepository, never()).findByToken(any());
        }

        @Test
        @DisplayName("DB에 refreshToken이 없으면 실패한다")
        void refreshAccessToken_notFound_throwsInvalidRefreshToken() {
            // given
            given(refreshTokenRepository.findByToken("refresh-token")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.refreshAccessToken("refresh-token"))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        @Test
        @DisplayName("DB의 refreshToken이 만료됐으면 삭제 후 실패한다")
        void refreshAccessToken_expired_throwsRefreshTokenExpired() {
            // given
            RefreshToken refreshToken = new RefreshToken(activeUser, "refresh-token", LocalDateTime.now().minusSeconds(1));
            given(refreshTokenRepository.findByToken("refresh-token")).willReturn(Optional.of(refreshToken));

            // when & then
            assertThatThrownBy(() -> userService.refreshAccessToken("refresh-token"))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);

            verify(refreshTokenRepository).delete(refreshToken);
            verify(jwtTokenProvider, never()).validateRefreshTokenOrThrow(any());
        }

        @Test
        @DisplayName("JWT 검증 실패 시 refreshToken을 삭제하고 실패한다")
        void refreshAccessToken_invalidJwt_deletesTokenAndThrows() {
            // given
            RefreshToken refreshToken = new RefreshToken(activeUser, "refresh-token", LocalDateTime.now().plusDays(1));
            given(refreshTokenRepository.findByToken("refresh-token")).willReturn(Optional.of(refreshToken));
            doThrow(new ApiException(ErrorCode.INVALID_REFRESH_TOKEN))
                    .when(jwtTokenProvider).validateRefreshTokenOrThrow("refresh-token");

            // when & then
            assertThatThrownBy(() -> userService.refreshAccessToken("refresh-token"))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

            verify(refreshTokenRepository).delete(refreshToken);
        }

        @Test
        @DisplayName("삭제된 유저의 refreshToken이면 삭제 후 실패한다")
        void refreshAccessToken_deletedUser_deletesTokenAndThrows() {
            // given
            activeUser.delete();
            RefreshToken refreshToken = new RefreshToken(activeUser, "refresh-token", LocalDateTime.now().plusDays(1));
            given(refreshTokenRepository.findByToken("refresh-token")).willReturn(Optional.of(refreshToken));

            // when & then
            assertThatThrownBy(() -> userService.refreshAccessToken("refresh-token"))
                    .isInstanceOf(ApiException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED_USER);

            verify(refreshTokenRepository).delete(refreshToken);
            verify(jwtTokenProvider, never()).createAccessToken(any());
        }
    }

    private SignUpRequestDto signUpRequest(
            String email,
            String password,
            String passwordCheck,
            String nickname,
            String profileImage
    ) {
        SignUpRequestDto request = new SignUpRequestDto();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "passwordCheck", passwordCheck);
        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "profileImage", profileImage);
        return request;
    }

    private LoginRequestDto loginRequest(String email, String password) {
        LoginRequestDto request = new LoginRequestDto();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }


    private UserUpdateRequestDto userUpdateRequest(String nickname, String profileImage) {
        UserUpdateRequestDto request = new UserUpdateRequestDto();
        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "profileImage", profileImage);
        return request;
    }

    private PasswordUpdateRequestDto passwordUpdateRequest(
            String curPassword,
            String password,
            String passwordCheck
    ) {
        PasswordUpdateRequestDto request = new PasswordUpdateRequestDto();
        ReflectionTestUtils.setField(request, "curPassword", curPassword);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "passwordCheck", passwordCheck);
        return request;
    }

    private User user(
            Long userId,
            String email,
            String password,
            String nickname,
            String profileImage
    ) {
        User user = new User(email, password, nickname, profileImage);
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
