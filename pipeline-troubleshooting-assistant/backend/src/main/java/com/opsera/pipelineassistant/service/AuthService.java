package com.opsera.pipelineassistant.service;

import com.opsera.pipelineassistant.model.User;
import com.opsera.pipelineassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public User register(String email, String password, String displayName) {
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        String token = UUID.randomUUID().toString();
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .displayName(displayName)
                .verificationToken(token)
                .verificationTokenExpiry(LocalDateTime.now().plusHours(24))
                .build();
        User saved = userRepository.save(user);
        emailService.sendVerificationEmail(email, token);
        log.info("Registered user '{}' — verification email dispatched", email);
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
}
