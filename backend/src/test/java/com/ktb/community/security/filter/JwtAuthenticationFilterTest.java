package com.ktb.community.security.filter;

import com.ktb.community.security.handler.CustomAuthenticationEntryPoint;
import com.ktb.community.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsUnauthorizedWhenTokenUserDoesNotExist() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        FilterChain filterChain = mock(FilterChain.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                tokenProvider,
                new CustomAuthenticationEntryPoint()
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.resolveToken(request)).thenReturn("valid-token");
        when(tokenProvider.validateAccessToken("valid-token")).thenReturn(true);
        when(tokenProvider.getAuthentication("valid-token"))
                .thenThrow(new UsernameNotFoundException("User not found: test@example.com"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString())
                .isEqualTo("{\"message\":\"unauthorized_user\",\"data\":null}");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }
}
