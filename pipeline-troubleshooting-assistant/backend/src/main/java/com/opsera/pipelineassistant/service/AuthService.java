package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.dto.Responses.RefreshResult;
import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final Pattern PASSWORD_COMPLEXITY =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationSeconds;

    @Transactional
    public User register(String email, String password, String displayName) {
        String normalizedEmail = email.trim().toLowerCase();

        if (password.length() < 12 || !PASSWORD_COMPLEXITY.matcher(password).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Registration could not be completed. Please try a different email.");
        }

        String token = UUID.randomUUID().toString();
        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName)
                .verificationToken(token)
                .verificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();
        User saved = userRepository.save(user);
        emailService.sendVerificationEmail(normalizedEmail, token);
        log.info("Registered user '{}' — verification email dispatched", normalizedEmail);
        return saved;
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid verification link."));

        if (user.getVerificationTokenExpiry() == null
                || user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.GONE, "Verification link has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
        log.info("Email verified for '{}'", user.getEmail());
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Session expired. Please log in again.");
        }

        String hash = hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session expired. Please log in again."));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteByTokenHash(hash);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Session expired. Please log in again.");
        }

        User user = stored.getUser();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRawRefreshToken = jwtTokenProvider.generateRefreshToken();
        String newHash = hashToken(newRawRefreshToken);

        refreshTokenRepository.deleteByTokenHash(hash);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(newHash)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationSeconds))
                .build());

        log.debug("Refresh token rotated for user '{}'", user.getEmail());
        return new RefreshResult(newAccessToken, newRawRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        try {
            String hash = hashToken(rawRefreshToken);
            refreshTokenRepository.deleteByTokenHash(hash);
            log.debug("Refresh token invalidated on logout");
        } catch (Exception e) {
            log.warn("Logout token cleanup failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return;
            }
            String token = UUID.randomUUID().toString();
            user.setVerificationToken(token);
            user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
            userRepository.save(user);
            emailService.sendVerificationEmail(email, token);
            log.info("Resent verification email to '{}'", email);
        });
    }

    String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
