package com.opsera.pipelineassistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
@Slf4j
public class ConsoleEmailService implements EmailService {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public void sendVerificationEmail(String email, String token) {
        String verificationUrl = baseUrl + "/api/auth/verify?token=" + token;
        log.info("[DEV] Email verification for {}: {}", email, verificationUrl);
    }
}
