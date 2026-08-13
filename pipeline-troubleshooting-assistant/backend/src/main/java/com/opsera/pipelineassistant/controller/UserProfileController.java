package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.ChangePasswordRequest;
import com.opsera.pipelineassistant.dto.Responses.SessionDTO;
import com.opsera.pipelineassistant.dto.Responses.UserProfileDTO;
import com.opsera.pipelineassistant.dto.UpdateProfileRequest;
import com.opsera.pipelineassistant.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public UserProfileDTO getProfile(Authentication authentication) {
        return userProfileService.getProfile(authentication.getName());
    }

    @PutMapping
    public UserProfileDTO updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        return userProfileService.updateProfile(authentication.getName(), request);
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userProfileService.changePassword(authentication.getName(), request);
        return Map.of("message", "Password changed successfully");
    }

    @GetMapping("/sessions")
    public List<SessionDTO> getSessions(Authentication authentication) {
        return userProfileService.getSessions(authentication.getName());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        userProfileService.revokeSession(authentication.getName(), sessionId);
    }
}
