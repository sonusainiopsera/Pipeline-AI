package com.opsera.pipelineassistant.security;

import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    // ── User found — happy path ───────────────────────────────────────────────

    @Test
    void loadUserByUsernameReturnsUserDetailsWhenUserExists() {
        User user = User.builder()
                .email("alice@example.com")
                .passwordHash("$2a$12$hashedPassword")
                .displayName("Alice")
                .role(Role.ANALYST)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("alice@example.com");

        assertThat(details.getUsername()).isEqualTo("alice@example.com");
        assertThat(details.getPassword()).isEqualTo("$2a$12$hashedPassword");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    // ── Authority mapping ────────────────────────────────────────────────────

    @Test
    void loadUserByUsernameGrantsRoleAnalystAuthority() {
        User user = User.builder()
                .email("analyst@example.com")
                .passwordHash("hash")
                .displayName("Analyst")
                .role(Role.ANALYST)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("analyst@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("analyst@example.com");

        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_ANALYST");
    }

    @Test
    void loadUserByUsernameGrantsRoleKbAdminAuthority() {
        User user = User.builder()
                .email("admin@example.com")
                .passwordHash("hash")
                .displayName("Admin")
                .role(Role.KB_ADMIN)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("admin@example.com");

        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_KB_ADMIN");
    }

    @Test
    void loadUserByUsernameGrantsRoleManagerAuthority() {
        User user = User.builder()
                .email("mgr@example.com")
                .passwordHash("hash")
                .displayName("Manager")
                .role(Role.MANAGER)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("mgr@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("mgr@example.com");

        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactly("ROLE_MANAGER");
    }

    // ── User not found ────────────────────────────────────────────────────────

    @Test
    void loadUserByUsernameThrowsUsernameNotFoundExceptionForUnknownEmail() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                customUserDetailsService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown@example.com");
    }

    // ── Account locked status ─────────────────────────────────────────────────

    @Test
    void loadUserByUsernameReflectsAccountLockedWhenLockedUntilIsInFuture() {
        User user = User.builder()
                .email("locked@example.com")
                .passwordHash("hash")
                .displayName("Locked")
                .role(Role.ANALYST)
                .emailVerified(true)
                .lockedUntil(LocalDateTime.now().plusHours(1))
                .build();
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("locked@example.com");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    void loadUserByUsernameAccountNotLockedWhenLockedUntilIsInPast() {
        User user = User.builder()
                .email("expired@example.com")
                .passwordHash("hash")
                .displayName("Expired Lock")
                .role(Role.ANALYST)
                .emailVerified(true)
                .lockedUntil(LocalDateTime.now().minusHours(1))
                .build();
        when(userRepository.findByEmail("expired@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("expired@example.com");

        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void loadUserByUsernameAccountNotLockedWhenLockedUntilIsNull() {
        User user = User.builder()
                .email("notlocked@example.com")
                .passwordHash("hash")
                .displayName("Not Locked")
                .role(Role.ANALYST)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("notlocked@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("notlocked@example.com");

        assertThat(details.isAccountNonLocked()).isTrue();
    }

    // ── Email verification status ─────────────────────────────────────────────

    @Test
    void loadUserByUsernameReflectsDisabledWhenEmailNotVerified() {
        User user = User.builder()
                .email("unverified@example.com")
                .passwordHash("hash")
                .displayName("Unverified")
                .role(Role.ANALYST)
                .emailVerified(false)
                .build();
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("unverified@example.com");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsernameReflectsEnabledWhenEmailVerified() {
        User user = User.builder()
                .email("verified@example.com")
                .passwordHash("hash")
                .displayName("Verified")
                .role(Role.ANALYST)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("verified@example.com");

        assertThat(details.isEnabled()).isTrue();
    }

    // ── Email normalization ───────────────────────────────────────────────────

    @Test
    void loadUserByUsernameNormalizesEmailToLowercase() {
        User user = User.builder()
                .email("alice@example.com")
                .passwordHash("hash")
                .displayName("Alice")
                .role(Role.ANALYST)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("ALICE@EXAMPLE.COM");

        assertThat(details.getUsername()).isEqualTo("alice@example.com");
    }
}
