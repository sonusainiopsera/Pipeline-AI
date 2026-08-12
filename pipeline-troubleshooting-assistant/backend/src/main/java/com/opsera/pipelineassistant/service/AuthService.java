package com.opsera.pipelineassistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.Responses.LoginResponse;
import com.opsera.pipelineassistant.dto.Responses.LoginResult;
import com.opsera.pipelineassistant.dto.Responses.RefreshResult;
import com.opsera.pipelineassistant.exception.AccountLockedException;
import com.opsera.pipelineassistant.exception.EmailNotVerifiedException;
import com.opsera.pipelineassistant.model.RefreshToken;
import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.RefreshTokenRepository;
import com.opsera.pipelineassistant.repository.UserRepository;
import com.opsera.pipelineassistant.security.AesEncryptionUtil;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.security.MfaService;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
    private final MfaService mfaService;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final ObjectMapper objectMapper;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationSeconds;

    @Transactional
    public LoginResult login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailNotVerifiedException("Please verify your email before logging in");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException(
                    "Account locked due to too many failed attempts. Try again in 15 minutes.");
        }

        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(LocalDateTime.now())) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 5) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                log.warn("Account '{}' locked after {} failed login attempts", normalizedEmail, attempts);
            }
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            String challengeToken = jwtTokenProvider.generateMfaChallengeToken(normalizedEmail);
            user.setMfaChallengeTokenHash(hashToken(challengeToken));
            userRepository.save(user);
            log.info("MFA challenge issued for '{}'", normalizedEmail);
            LoginResponse profile = new LoginResponse(
                    user.getEmail(), user.getDisplayName(), user.getRole().name(), true);
            return new LoginResult(null, null, profile, challengeToken);
        }

        userRepository.save(user);

        log.info("Login successful for '{}'", normalizedEmail);
        return issueFullTokens(user);
    }

    @Transactional
    public LoginResult verifyMfaChallenge(String challengeToken, String code) {
        if (!jwtTokenProvider.validateMfaChallengeToken(challengeToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired MFA challenge token. Please log in again.");
        }

        String email = jwtTokenProvider.extractMfaChallengeEmail(challengeToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid MFA challenge"));

        String tokenHash = hashToken(challengeToken);
        if (!tokenHash.equals(user.getMfaChallengeTokenHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "MFA challenge token has already been used. Please log in again.");
        }
        // Invalidate single-use token before verification to prevent replay regardless of outcome
        user.setMfaChallengeTokenHash(null);
        userRepository.save(user);

        String decryptedSecret;
        try {
            decryptedSecret = aesEncryptionUtil.decrypt(user.getMfaSecret());
        } catch (Exception e) {
            log.error("Failed to decrypt MFA secret for '{}'", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "MFA verification failed");
        }

        if (!mfaService.verifyCode(decryptedSecret, code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication code");
        }

        log.info("MFA challenge verified for '{}'", email);
        return issueFullTokens(user);
    }

    @Transactional
    public LoginResult verifyMfaRecovery(String challengeToken, String recoveryCode) {
        if (!jwtTokenProvider.validateMfaChallengeToken(challengeToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired MFA challenge token. Please log in again.");
        }

        String email = jwtTokenProvider.extractMfaChallengeEmail(challengeToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid MFA challenge"));

        String tokenHash = hashToken(challengeToken);
        if (!tokenHash.equals(user.getMfaChallengeTokenHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "MFA challenge token has already been used. Please log in again.");
        }
        user.setMfaChallengeTokenHash(null);

        if (user.getRecoveryCodes() == null || user.getRecoveryCodes().isBlank()) {
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "No recovery codes available. Please contact your administrator.");
        }

        List<String> codeHashes;
        try {
            codeHashes = new ArrayList<>(objectMapper.readValue(
                    user.getRecoveryCodes(), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            log.error("Failed to parse recovery codes for '{}'", email);
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Recovery code verification failed");
        }

        String normalizedCode = recoveryCode.trim().toUpperCase();
        int matchIndex = -1;
        for (int i = 0; i < codeHashes.size(); i++) {
            String storedHash = codeHashes.get(i);
            if (storedHash != null && passwordEncoder.matches(normalizedCode, storedHash)) {
                matchIndex = i;
                break;
            }
        }

        if (matchIndex == -1) {
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid recovery code");
        }

        codeHashes.set(matchIndex, null);
        long remaining = codeHashes.stream().filter(c -> c != null).count();
        if (remaining == 0) {
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "All recovery codes have been used. Please contact your administrator.");
        }

        try {
            user.setRecoveryCodes(objectMapper.writeValueAsString(codeHashes));
        } catch (Exception e) {
            log.error("Failed to serialize updated recovery codes for '{}'", email);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Recovery code verification failed");
        }

        userRepository.save(user);
        log.info("MFA recovery used for '{}' — {} codes remaining", email, remaining);
        return issueFullTokens(user);
    }

    private LoginResult issueFullTokens(User user) {
        long sessionCount = refreshTokenRepository.countByUserId(user.getId());
        if (sessionCount >= 3) {
            refreshTokenRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                    .ifPresent(oldest -> refreshTokenRepository.deleteByTokenHash(oldest.getTokenHash()));
        }

        String accessToken    = jwtTokenProvider.generateAccessToken(user);
        String rawRefreshToken = jwtTokenProvider.generateRefreshToken();
        String tokenHash      = hashToken(rawRefreshToken);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationSeconds))
                .build());

        LoginResponse profile = new LoginResponse(
                user.getEmail(), user.getDisplayName(), user.getRole().name(), false);
        return new LoginResult(accessToken, rawRefreshToken, profile, null);
    }

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
