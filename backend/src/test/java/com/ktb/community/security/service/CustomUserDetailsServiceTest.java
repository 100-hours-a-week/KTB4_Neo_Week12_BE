package com.ktb.community.security.service;

import com.ktb.community.domain.user.entity.User;
import com.ktb.community.domain.user.repository.UserRepository;
import com.ktb.community.security.principal.CustomPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CustomUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

    @Test
    void loadsActiveUserByIdWithoutPassword() {
        User user = new User("user@example.com", "encoded-secret", "neo", null);
        ReflectionTestUtils.setField(user, "userId", 1L);
        given(userRepository.findByUserIdAndDeletedFalse(1L)).willReturn(Optional.of(user));

        CustomPrincipal principal = service.loadUserById(1L);

        assertThat(principal.getUserId()).isEqualTo(1L);
        assertThat(principal.getUsername()).isEqualTo("user@example.com");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(principal.getPassword()).isNull();
    }

    @Test
    void rejectsMissingOrDeletedUser() {
        given(userRepository.findByUserIdAndDeletedFalse(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserById(1L))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
