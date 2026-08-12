package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceVerificationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private User unverifiedUser;

    @BeforeEach
    void setUp() {
        unverifiedUser = User.builder()
                .email("test@example.com")
                .passwordHash("hashed")
                .displayName("Test User")
                .verificationToken(UUID.randomUUID().toString())
                .verificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .emailVerified(false)
                .build();
    }

    @Test
    void register_generatesTokenAndCallsEmailService() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register("new@example.com", "pass", "New User");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertThat(saved.getVerificationToken()).isNotNull();
        assertThat(saved.getVerificationToken()).hasSize(36); // UUID format
        assertThat(saved.getVerificationTokenExpiry()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(saved.getEmailVerified()).isFalse();

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq("new@example.com"), tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isEqualTo(saved.getVerificationToken());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("dupe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("dupe@example.com", "pass", "Name"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void verifyEmail_validToken_activatesAccount() {
        String token = unverifiedUser.getVerificationToken();
        when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(unverifiedUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.verifyEmail(token);

        assertThat(unverifiedUser.getEmailVerified()).isTrue();
        assertThat(unverifiedUser.getVerificationToken()).isNull();
        assertThat(unverifiedUser.getVerificationTokenExpiry()).isNull();
        verify(userRepository).save(unverifiedUser);
    }

    @Test
    void verifyEmail_expiredToken_throwsGone() {
        unverifiedUser.setVerificationTokenExpiry(LocalDateTime.now().minusSeconds(1));
        String token = unverifiedUser.getVerificationToken();
        when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(unverifiedUser));

        assertThatThrownBy(() -> authService.verifyEmail(token))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.GONE));
    }

    @Test
    void verifyEmail_invalidToken_throwsBadRequest() {
        when(userRepository.findByVerificationToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void verifyEmail_nullExpiry_throwsGone() {
        unverifiedUser.setVerificationTokenExpiry(null);
        String token = unverifiedUser.getVerificationToken();
        when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(unverifiedUser));

        assertThatThrownBy(() -> authService.verifyEmail(token))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.GONE));
    }

    @Test
    void resendVerification_unverifiedUser_generatesNewToken() {
        String oldToken = unverifiedUser.getVerificationToken();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resendVerification("test@example.com");

        assertThat(unverifiedUser.getVerificationToken()).isNotNull();
        assertThat(unverifiedUser.getVerificationToken()).isNotEqualTo(oldToken);
        assertThat(unverifiedUser.getVerificationTokenExpiry()).isAfter(LocalDateTime.now().plusHours(23));
        verify(emailService).sendVerificationEmail(eq("test@example.com"), eq(unverifiedUser.getVerificationToken()));
    }

    @Test
    void resendVerification_alreadyVerified_doesNotSendEmail() {
        unverifiedUser.setEmailVerified(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(unverifiedUser));

        authService.resendVerification("test@example.com");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resendVerification_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.resendVerification("unknown@example.com");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
        verify(userRepository, never()).save(any());
    }
}
