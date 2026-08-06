package com.ktb.community.security.principal;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomPrincipalTest {

    @Test
    void exposesOnlyImmutableAuthenticationInformation() {
        List<SimpleGrantedAuthority> source = new ArrayList<>();
        source.add(new SimpleGrantedAuthority("ROLE_USER"));

        CustomPrincipal principal = new CustomPrincipal(1L, "user@example.com", source);
        source.clear();

        assertThat(principal.getUserId()).isEqualTo(1L);
        assertThat(principal.getUsername()).isEqualTo("user@example.com");
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.toString()).doesNotContain("password");
        assertThatThrownBy(() -> principal.getAuthorities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidIdentity() {
        assertThatThrownBy(() -> new CustomPrincipal(0L, "user@example.com", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomPrincipal(1L, " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
