package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.audit.AuditService;
import com.opsera.pipelineassistant.dto.ChangePasswordRequest;
import com.opsera.pipelineassistant.dto.Responses.SessionDTO;
import com.opsera.pipelineassistant.dto.Responses.UserProfileDTO;
import com.opsera.pipelineassistant.dto.UpdateProfileRequest;
import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private static final Pattern PASSWORD_COMPLEXITY =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).*$");

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserProfileDTO getProfile(String email) {
        User user = findUserByEmail(email);
        return UserProfileDTO.from(user);
    }

    @Transactional
    public UserProfileDTO updateProfile(String email, UpdateProfileRequest request) {
        User user = findUserByEmail(email);
        String oldDisplayName = user.getDisplayName();
        user.setDisplayName(request.getDisplayName());
        User saved = userRepository.save(user);
        try {
            auditService.logEvent("PROFILE_UPDATE", AuditService.RESOURCE_USER,
                    user.getId().toString(),
                    Map.of("oldDisplayName", oldDisplayName, "newDisplayName", request.getDisplayName()));
        } catch (Exception e) {
            log.warn("Failed to audit profile update for '{}': {}", email, e.getMessage());
        }
        return UserProfileDTO.from(saved);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findUserByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from current password");
        }

        if (request.getNewPassword().length() < 12
                || !PASSWORD_COMPLEXITY.matcher(request.getNewPassword()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 12 characters and contain uppercase, lowercase, digit, and special character");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteByUserId(user.getId());

        try {
            auditService.logEvent("PASSWORD_CHANGE", AuditService.RESOURCE_USER,
                    user.getId().toString(), null);
        } catch (Exception e) {
            log.warn("Failed to audit password change for '{}': {}", email, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<SessionDTO> getSessions(String email) {
        User user = findUserByEmail(email);
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(user.getId());
        return tokens.stream().map(SessionDTO::from).toList();
    }

    @Transactional
    public void revokeSession(String email, UUID sessionId) {
        User user = findUserByEmail(email);
        RefreshToken token = refreshTokenRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        refreshTokenRepository.delete(token);
        try {
            auditService.logEvent("SESSION_REVOKE", AuditService.RESOURCE_SESSION,
                    sessionId.toString(), null);
        } catch (Exception e) {
            log.warn("Failed to audit session revoke for '{}': {}", email, e.getMessage());
        }
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found"));
    }
}
