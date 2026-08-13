package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.ErrorRequest;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ErrorController.class)
class ErrorControllerRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private ErrorRequest validRequest() {
        ErrorRequest req = new ErrorRequest();
        req.setErrorPattern("oom,heap");
        req.setCategory("Memory");
        req.setRootCause("Heap exhausted");
        req.setSolution("Increase Xmx");
        req.setSeverity("HIGH");
        return req;
    }

    private ErrorKnowledgeBase entity(long id) {
        return ErrorKnowledgeBase.builder()
                .id(id).errorPattern("oom,heap").category("Memory")
                .rootCause("Heap exhausted").solution("Increase Xmx").severity("HIGH").build();
    }

    private void stubFindAll() {
        when(knowledgeBaseService.findAll()).thenReturn(List.of(entity(1L)));
    }

    private void stubCreate() {
        when(knowledgeBaseService.create(any())).thenReturn(entity(1L));
    }

    private void stubUpdate() {
        when(knowledgeBaseService.update(anyLong(), any())).thenReturn(entity(1L));
    }

    private void stubDelete() {
        doNothing().when(knowledgeBaseService).delete(anyLong());
    }

    // ── GET /api/errors — all 3 roles allowed ─────────────────────────────────

    @Test
    @WithMockUser(roles = "ANALYST")
    void findAll_analyst_returns200() throws Exception {
        stubFindAll();
        mockMvc.perform(get("/api/errors")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void findAll_kbAdmin_returns200() throws Exception {
        stubFindAll();
        mockMvc.perform(get("/api/errors")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void findAll_manager_returns200() throws Exception {
        stubFindAll();
        mockMvc.perform(get("/api/errors")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void findAll_unknownRole_returns403() throws Exception {
        mockMvc.perform(get("/api/errors")).andExpect(status().isForbidden());
    }

    // ── POST /api/errors — ANALYST, KB_ADMIN, and MANAGER ─────────────────────

    @Test
    @WithMockUser(roles = "ANALYST")
    void create_analyst_returns201() throws Exception {
        stubCreate();
        mockMvc.perform(post("/api/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void create_kbAdmin_returns201() throws Exception {
        stubCreate();
        mockMvc.perform(post("/api/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void create_manager_returns201() throws Exception {
        stubCreate();
        mockMvc.perform(post("/api/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    // ── PUT /api/errors/{id} — ANALYST, KB_ADMIN, and MANAGER ────────────────

    @Test
    @WithMockUser(roles = "ANALYST")
    void update_analyst_returns200() throws Exception {
        stubUpdate();
        mockMvc.perform(put("/api/errors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void update_kbAdmin_returns200() throws Exception {
        stubUpdate();
        mockMvc.perform(put("/api/errors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void update_manager_returns200() throws Exception {
        stubUpdate();
        mockMvc.perform(put("/api/errors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk());
    }

    // ── DELETE /api/errors/{id} — ANALYST, KB_ADMIN, and MANAGER ─────────────

    @Test
    @WithMockUser(roles = "ANALYST")
    void delete_analyst_returns204() throws Exception {
        stubDelete();
        mockMvc.perform(delete("/api/errors/1")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "KB_ADMIN")
    void delete_kbAdmin_returns204() throws Exception {
        stubDelete();
        mockMvc.perform(delete("/api/errors/1")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void delete_manager_returns204() throws Exception {
        stubDelete();
        mockMvc.perform(delete("/api/errors/1")).andExpect(status().isNoContent());
    }
}
