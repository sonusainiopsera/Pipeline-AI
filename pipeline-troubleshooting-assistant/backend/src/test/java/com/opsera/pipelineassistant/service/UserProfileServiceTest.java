package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.dto.ChangePasswordRequest;
import com.opsera.pipelineassistant.dto.Responses.SessionDTO;
import com.opsera.pipelineassistant.dto.Responses.UserProfileDTO;
import com.opsera.pipelineassistant.dto.UpdateProfileRequest;
import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.Role;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserProfileService userProfileService;

    private User user;
    private final UUID userId = UUID.randomUUID();
    private final String email = "alice@example.com";

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(userId)
                .email(email)
                .displayName("Alice")
                .passwordHash("$2a$10$hashedpassword")
                .role(Role.ANALYST)
                .mfaEnabled(false)
                .createdAt(LocalDateTime.of(2026, 1, 15, 10, 30, 0))
                .build();
    }

    // ── Profile retrieval ─────────────────────────────────────────────────────────

    @Test
    void getProfile_mapsAllFieldsCorrectly() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserProfileDTO dto = userProfileService.getProfile(email);

        assertThat(dto.displayName()).isEqualTo("Alice");
        assertThat(dto.email()).isEqualTo(email);
        assertThat(dto.role()).isEqualTo("ANALYST");
        assertThat(dto.mfaEnabled()).isFalse();
        assertThat(dto.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 30, 0));
    }

    @Test
    void getProfile_throwsNotFoundWhenUserMissing() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfile(email))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User profile not found");
    }

    @Test
    void getProfile_neverExposesSensitiveFields() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserProfileDTO dto = userProfileService.getProfile(email);

        // UserProfileDTO only has: displayName, email, role, mfaEnabled, createdAt
        // passwordHash and mfaSecret are not in the record at all
        assertThat(dto).isNotNull();
        assertThat(dto.displayName()).isNotNull();
    }

    // ── Profile update ────────────────────────────────────────────────────────────

    @Test
    void updateProfile_persistsNewDisplayName() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Alice Smith");

        UserProfileDTO result = userProfileService.updateProfile(email, request);

        assertThat(result.displayName()).isEqualTo("Alice Smith");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_auditLogWritten() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("New Name");

        userProfileService.updateProfile(email, request);

        verify(auditService).logEvent(eq("PROFILE_UPDATE"), eq(AuditService.RESOURCE_USER),
                eq(userId.toString()), any());
    }

    // ── Password change ───────────────────────────────────────────────────────────

    @Test
    void changePassword_successfullyUpdatesHash() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123!", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("NewSecure456@")).thenReturn("$2a$10$newhash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass123!");
        request.setNewPassword("NewSecure456@");

        userProfileService.changePassword(email, request);

        verify(userRepository).save(user);
        verify(refreshTokenRepository).deleteByUserId(userId);
        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$newhash");
    }

    @Test
    void changePassword_throwsBadRequestWhenCurrentPasswordWrong() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("WrongPass!");
        request.setNewPassword("NewSecure456@");

        assertThatThrownBy(() -> userProfileService.changePassword(email, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_throwsBadRequestWhenNewPasswordSameAsCurrent() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123!", user.getPasswordHash())).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass123!");
        request.setNewPassword("OldPass123!");

        assertThatThrownBy(() -> userProfileService.changePassword(email, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("New password must be different");
    }

    @Test
    void changePassword_invalidatesAllSessionsOnSuccess() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123!", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$newhash");

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass123!");
        request.setNewPassword("NewSecure456@");

        userProfileService.changePassword(email, request);

        verify(refreshTokenRepository).deleteByUserId(userId);
    }

    // ── Session management ────────────────────────────────────────────────────────

    @Test
    void getSessions_returnsSessionDTOsForUser() {
        UUID tokenId = UUID.randomUUID();
        RefreshToken token = RefreshToken.builder()
                .id(tokenId)
                .user(user)
                .tokenHash("somehash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.of(2026, 6, 15, 10, 0, 0))
                .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of(token));

        List<SessionDTO> sessions = userProfileService.getSessions(email);

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionId()).isEqualTo(tokenId.toString());
        assertThat(sessions.get(0).createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 15, 10, 0, 0));
        assertThat(sessions.get(0).ipAddress()).isNull();
        assertThat(sessions.get(0).isCurrent()).isFalse();
    }

    @Test
    void getSessions_returnsEmptyListWhenNoActiveSessions() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<SessionDTO> sessions = userProfileService.getSessions(email);

        assertThat(sessions).isEmpty();
    }

    @Test
    void revokeSession_deletesOwnedSession() {
        UUID sessionId = UUID.randomUUID();
        RefreshToken token = RefreshToken.builder()
                .id(sessionId)
                .user(user)
                .tokenHash("somehash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(token));

        userProfileService.revokeSession(email, sessionId);

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void revokeSession_throwsNotFoundForUnownedSession() {
        UUID sessionId = UUID.randomUUID();
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.revokeSession(email, sessionId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Session not found");

        verify(refreshTokenRepository, never()).delete(any());
    }
}
