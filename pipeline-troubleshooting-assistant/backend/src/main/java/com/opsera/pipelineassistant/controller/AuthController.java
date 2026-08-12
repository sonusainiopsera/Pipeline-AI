package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.ResendVerificationRequest;
import com.opsera.pipelineassistant.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        log.info("GET /api/auth/verify");
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email verified successfully. You can now log in."));
    }

    @PostMapping("/verify/resend")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        log.info("POST /api/auth/verify/resend");
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "Verification email sent."));
    }
}
