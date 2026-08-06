package com.ktb.community.security.service;

import com.ktb.community.domain.user.entity.User;
import com.ktb.community.domain.user.repository.UserRepository;
import com.ktb.community.security.principal.CustomPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return toPrincipal(user);
    }

    public CustomPrincipal loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        return toPrincipal(user);
    }

    private CustomPrincipal toPrincipal(User user) {
        return new CustomPrincipal(
                user.getUserId(),
                user.getEmail(),
                java.util.List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}
