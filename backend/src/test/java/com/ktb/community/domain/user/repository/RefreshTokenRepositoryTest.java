package com.ktb.community.domain.user.repository;

import com.ktb.community.domain.user.entity.RefreshToken;
import com.ktb.community.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("저장된 refreshToken 값으로 토큰을 조회한다")
    void findByToken_returnsMatchingRefreshToken() {
        // given
        User user = userRepository.save(user("test@example.com", "neo"));
        RefreshToken refreshToken = refreshTokenRepository.save(
                new RefreshToken(user, "refresh-token", LocalDateTime.now().plusDays(7))
        );

        // when & then
        assertThat(refreshTokenRepository.findByToken("refresh-token"))
                .isPresent()
                .get()
                .extracting(RefreshToken::getId)
                .isEqualTo(refreshToken.getId());
        assertThat(refreshTokenRepository.findByToken("unknown-token")).isEmpty();
    }

    @Test
    @DisplayName("유저로 refreshToken을 조회한다")
    void findByUser_returnsMatchingRefreshToken() {
        // given
        User user = userRepository.save(user("test@example.com", "neo"));
        refreshTokenRepository.save(
                new RefreshToken(user, "refresh-token", LocalDateTime.now().plusDays(7))
        );

        // when & then
        assertThat(refreshTokenRepository.findByUser(user))
                .isPresent()
                .get()
                .extracting(RefreshToken::getToken)
                .isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("유저의 refreshToken을 삭제한다")
    void deleteByUser_deletesRefreshToken() {
        // given
        User user = userRepository.save(user("test@example.com", "neo"));
        refreshTokenRepository.save(
                new RefreshToken(user, "refresh-token", LocalDateTime.now().plusDays(7))
        );

        // when
        refreshTokenRepository.deleteByUser(user);
        refreshTokenRepository.flush();

        // then
        assertThat(refreshTokenRepository.findByUser(user)).isEmpty();
    }

    private User user(String email, String nickname) {
        return new User(email, "encoded-password", nickname, "/profile.png");
    }
}
