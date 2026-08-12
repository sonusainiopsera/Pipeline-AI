package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private static final String VALID_PASSWORD = "SecurePass123!";
    private static final String VALID_EMAIL = "user@example.com";
    private static final String DISPLAY_NAME = "Test User";

    @Test
    void register_successfulRegistration_returns201UserWithDefaults() {
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = authService.register(VALID_EMAIL, VALID_PASSWORD, DISPLAY_NAME);

        assertThat(result.getEmail()).isEqualTo(VALID_EMAIL);
        assertThat(result.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(result.getPasswordHash()).isEqualTo("$2a$12$hashed");
        assertThat(result.getEmailVerified()).isFalse();
        assertThat(result.getMfaEnabled()).isFalse();
        assertThat(result.getRole()).isEqualTo(Role.ANALYST);
    }

    @Test
    void register_normalizesEmailToLowercase() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register("  USER@EXAMPLE.COM  ", VALID_PASSWORD, DISPLAY_NAME);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(VALID_EMAIL, VALID_PASSWORD, DISPLAY_NAME))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason())
                            .contains("Registration could not be completed");
                });

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordTooShort_throwsBadRequest() {
        assertThatThrownBy(() -> authService.register(VALID_EMAIL, "Short1!", DISPLAY_NAME))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_passwordNoUppercase_throwsBadRequest() {
        assertThatThrownBy(() -> authService.register(VALID_EMAIL, "nouppercase123!", DISPLAY_NAME))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_passwordNoLowercase_throwsBadRequest() {
        assertThatThrownBy(() -> authService.register(VALID_EMAIL, "NOLOWERCASE123!", DISPLAY_NAME))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_passwordNoDigit_throwsBadRequest() {
        assertThatThrownBy(() -> authService.register(VALID_EMAIL, "NoDigitHere!!!", DISPLAY_NAME))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void register_passwordNoSpecialChar_throwsBadRequest() {
        assertThatThrownBy(() -> authService.register(VALID_EMAIL, "NoSpecialChar12", DISPLAY_NAME))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SecurePass123!", "Abcdefghijkl1!", "P@ssw0rd!!!!!", "Tr0ub4dor&3abc"})
    void register_validPasswords_doNotThrow(String password) {
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(VALID_EMAIL, password, DISPLAY_NAME);

        verify(userRepository).save(any());
    }

    @Test
    void register_passwordExactly12Chars_accepted() {
        String exactly12 = "SecurePas1!a";
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(exactly12)).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(VALID_EMAIL, exactly12, DISPLAY_NAME);

        verify(userRepository).save(any());
    }

    @Test
    void register_rawPasswordNeverPersisted() {
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(VALID_EMAIL, VALID_PASSWORD, DISPLAY_NAME);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).doesNotContain(VALID_PASSWORD);
    }

    @Test
    void register_setsVerificationTokenForEmailFlow() {
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(VALID_PASSWORD)).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(VALID_EMAIL, VALID_PASSWORD, DISPLAY_NAME);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getVerificationToken()).isNotNull();
        assertThat(captor.getValue().getVerificationTokenExpiry()).isNotNull();
    }
}
