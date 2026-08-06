package com.ktb.community.security.principal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * JWT authentication principal. Passwords deliberately remain outside the
 * SecurityContext; the current login flow verifies them in UserService.
 */
public final class CustomPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final List<GrantedAuthority> authorities;

    public CustomPrincipal(
            Long userId,
            String username,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.username = Objects.requireNonNull(username, "username must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        Objects.requireNonNull(authorities, "authorities must not be null");
        this.authorities = List.copyOf(authorities);
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "CustomPrincipal{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", authorities=" + authorities +
                '}';
    }
}
