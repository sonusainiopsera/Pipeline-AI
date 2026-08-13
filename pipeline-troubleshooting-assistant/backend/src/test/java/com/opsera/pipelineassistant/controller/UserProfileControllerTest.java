package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.ChangePasswordRequest;
import com.opsera.pipelineassistant.dto.Responses.SessionDTO;
import com.opsera.pipelineassistant.dto.Responses.UserProfileDTO;
import com.opsera.pipelineassistant.dto.UpdateProfileRequest;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserProfileController.class)
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String EMAIL = "alice@example.com";

    private UserProfileDTO sampleProfile() {
        return new UserProfileDTO("Alice", EMAIL, "ANALYST", false,
                LocalDateTime.of(2026, 1, 15, 10, 30, 0));
    }

    private SessionDTO sampleSession() {
        return new SessionDTO(UUID.randomUUID().toString(),
                LocalDateTime.of(2026, 6, 15, 10, 0, 0),
                LocalDateTime.of(2026, 6, 15, 10, 0, 0),
                null, false);
    }

    // ── Authentication requirement ────────────────────────────────────────────────

    @Test
    void getProfile_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alice\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/users/me/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Old123!\",\"newPassword\":\"New1234567@A\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSessions_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me/sessions"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/users/me ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void getProfile_returns200WithProfileDTO() throws Exception {
        when(userProfileService.getProfile(EMAIL)).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Alice")))
                .andExpect(jsonPath("$.email", is(EMAIL)))
                .andExpect(jsonPath("$.role", is("ANALYST")))
                .andExpect(jsonPath("$.mfaEnabled", is(false)))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getProfile_returns404WhenUserNotFound() throws Exception {
        when(userProfileService.getProfile(EMAIL))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "User profile not found"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/users/me ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateProfile_returns200WithUpdatedProfile() throws Exception {
        UserProfileDTO updated = new UserProfileDTO("Alice Smith", EMAIL, "ANALYST", false,
                LocalDateTime.of(2026, 1, 15, 10, 30, 0));
        when(userProfileService.updateProfile(eq(EMAIL), any(UpdateProfileRequest.class)))
                .thenReturn(updated);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Alice Smith");

        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName", is("Alice Smith")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateProfile_returns400WhenDisplayNameBlank() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateProfile_returns400WhenDisplayNameMissing() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/users/me/change-password ────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void changePassword_returns200WithSuccessMessage() throws Exception {
        doNothing().when(userProfileService).changePassword(eq(EMAIL), any(ChangePasswordRequest.class));

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass123!");
        request.setNewPassword("NewSecure456@");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Password changed successfully")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void changePassword_returns400WhenCurrentPasswordIncorrect() throws Exception {
        doThrow(new ResponseStatusException(BAD_REQUEST, "Current password is incorrect"))
                .when(userProfileService).changePassword(eq(EMAIL), any(ChangePasswordRequest.class));

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("WrongPass!");
        request.setNewPassword("NewSecure456@");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void changePassword_returns400WhenNewPasswordTooShort() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass123!");
        request.setNewPassword("Short1!");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/users/me/sessions ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void getSessions_returns200WithSessionList() throws Exception {
        when(userProfileService.getSessions(EMAIL)).thenReturn(List.of(sampleSession()));

        mockMvc.perform(get("/api/users/me/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sessionId", notNullValue()))
                .andExpect(jsonPath("$[0].isCurrent", is(false)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getSessions_returns200WithEmptyListWhenNoActiveSessions() throws Exception {
        when(userProfileService.getSessions(EMAIL)).thenReturn(List.of());

        mockMvc.perform(get("/api/users/me/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── DELETE /api/users/me/sessions/{sessionId} ─────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void revokeSession_returns204OnSuccess() throws Exception {
        UUID sessionId = UUID.randomUUID();
        doNothing().when(userProfileService).revokeSession(eq(EMAIL), eq(sessionId));

        mockMvc.perform(delete("/api/users/me/sessions/" + sessionId)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void revokeSession_returns404WhenSessionNotFound() throws Exception {
        UUID sessionId = UUID.randomUUID();
        doThrow(new ResponseStatusException(NOT_FOUND, "Session not found"))
                .when(userProfileService).revokeSession(eq(EMAIL), eq(sessionId));

        mockMvc.perform(delete("/api/users/me/sessions/" + sessionId)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
