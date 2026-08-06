package com.ktb.community.security.jwt;

import com.ktb.community.domain.user.entity.User;
import com.ktb.community.security.principal.CustomPrincipal;
import com.ktb.community.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class JwtTokenProviderTest {

    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private JwtTokenProvider provider;
    private User tokenUser;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(userDetailsService);
        ReflectionTestUtils.setField(provider, "secretKey", "test-secret-key-that-is-at-least-thirty-two-bytes-long");
        ReflectionTestUtils.setField(provider, "accessTokenValidityInMilliseconds", 60_000L);
        ReflectionTestUtils.setField(provider, "refreshTokenValidityInMilliseconds", 60_000L);
        provider.init();

        tokenUser = new User("user@example.com", "encoded-secret", "neo", null);
        ReflectionTestUtils.setField(tokenUser, "userId", 1L);
    }

    @Test
    void restoresCustomPrincipalFromUserIdAndDatabaseAuthority() {
        CustomPrincipal principal = principal("user@example.com", "ROLE_ADMIN");
        given(userDetailsService.loadUserById(1L)).willReturn(principal);

        Authentication authentication = provider.getAuthentication(provider.createAccessToken(tokenUser));

        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test
    void rejectsTokenWhenSubjectDoesNotMatchLoadedUser() {
        given(userDetailsService.loadUserById(1L))
                .willReturn(principal("replacement@example.com", "ROLE_USER"));

        assertThatThrownBy(() -> provider.getAuthentication(provider.createAccessToken(tokenUser)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsMalformedAndRefreshTokensAsAccessTokens() {
        assertThat(provider.validateAccessToken("not-a-jwt")).isFalse();
        assertThat(provider.validateAccessToken(provider.createRefreshToken(tokenUser))).isFalse();
    }

    @Test
    void rejectsExpiredAccessToken() {
        ReflectionTestUtils.setField(provider, "accessTokenValidityInMilliseconds", -1L);

        assertThat(provider.validateAccessToken(provider.createAccessToken(tokenUser))).isFalse();
    }

    private CustomPrincipal principal(String email, String role) {
        return new CustomPrincipal(1L, email, List.of(new SimpleGrantedAuthority(role)));
    }
}
