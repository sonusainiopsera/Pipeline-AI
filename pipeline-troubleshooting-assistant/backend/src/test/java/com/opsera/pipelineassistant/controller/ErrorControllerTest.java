package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.ErrorRequest;
import com.opsera.pipelineassistant.model.ErrorKnowledgeBase;
import com.opsera.pipelineassistant.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ErrorController.class)
class ErrorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    private ErrorKnowledgeBase buildEntity(Long id, String errorPattern, String category,
                                            String rootCause, String solution, String severity) {
        return ErrorKnowledgeBase.builder()
                .id(id)
                .errorPattern(errorPattern)
                .category(category)
                .rootCause(rootCause)
                .solution(solution)
                .severity(severity)
                .build();
    }

    private ErrorRequest buildRequest(String errorPattern, String category,
                                       String rootCause, String solution, String severity) {
        ErrorRequest req = new ErrorRequest();
        req.setErrorPattern(errorPattern);
        req.setCategory(category);
        req.setRootCause(rootCause);
        req.setSolution(solution);
        req.setSeverity(severity);
        return req;
    }

    // ── 1. GET /api/errors returns 200 with JSON array of 3 entries ───────────
    @Test
    void shouldReturnAllKnowledgeBaseEntries() throws Exception {
        List<ErrorKnowledgeBase> entries = List.of(
                buildEntity(1L, "oom,heap", "Memory", "Heap exhausted", "Increase Xmx", "HIGH"),
                buildEntity(2L, "timeout", "Network", "Connection timed out", "Check net", "MEDIUM"),
                buildEntity(3L, "docker,build", "Docker", "Build failed", "Fix Dockerfile", "HIGH")
        );
        when(knowledgeBaseService.findAll()).thenReturn(entries);

        mockMvc.perform(get("/api/errors"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].category", is("Memory")))
                .andExpect(jsonPath("$[1].category", is("Network")))
                .andExpect(jsonPath("$[2].category", is("Docker")));
    }

    // ── 2. GET /api/errors returns 200 with empty array ───────────────────────
    @Test
    void shouldReturnEmptyListWhenNoEntries() throws Exception {
        when(knowledgeBaseService.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/errors"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── 3. POST /api/errors with valid body returns 201 with entity ───────────
    @Test
    void shouldCreateNewEntry() throws Exception {
        ErrorRequest request = buildRequest("oom,heap", "Memory", "Heap exhausted", "Increase Xmx", "HIGH");
        ErrorKnowledgeBase created = buildEntity(1L, "oom,heap", "Memory", "Heap exhausted", "Increase Xmx", "HIGH");
        when(knowledgeBaseService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.category", is("Memory")))
                .andExpect(jsonPath("$.errorPattern", is("oom,heap")))
                .andExpect(jsonPath("$.severity", is("HIGH")));
    }

    // ── 4. POST /api/errors with blank category returns 400 ──────────────────
    @Test
    void shouldReturn400WhenCreatingWithBlankCategory() throws Exception {
        ErrorRequest request = buildRequest("oom,heap", "", "Heap exhausted", "Increase Xmx", "HIGH");

        mockMvc.perform(post("/api/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── 5. POST /api/errors with blank errorPattern returns 400 ──────────────
    @Test
    void shouldReturn400WhenCreatingWithBlankErrorPattern() throws Exception {
        ErrorRequest request = buildRequest("", "Memory", "Heap exhausted", "Increase Xmx", "HIGH");

        mockMvc.perform(post("/api/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── 6. POST /api/errors with all blank fields returns 400 ────────────────
    @Test
    void shouldReturn400WhenCreatingWithAllBlankFields() throws Exception {
        ErrorRequest request = buildRequest("", "", "", "", "");

        mockMvc.perform(post("/api/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── 7. POST /api/errors with missing body returns 400 ────────────────────
    @Test
    void shouldReturn400WhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/errors")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ── 8. PUT /api/errors/{id} with valid body returns 200 ──────────────────
    @Test
    void shouldUpdateExistingEntry() throws Exception {
        ErrorRequest request = buildRequest("updated,pattern", "UpdatedCategory",
                "Updated cause", "Updated fix", "MEDIUM");
        ErrorKnowledgeBase updated = buildEntity(1L, "updated,pattern", "UpdatedCategory",
                "Updated cause", "Updated fix", "MEDIUM");
        when(knowledgeBaseService.update(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/errors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.category", is("UpdatedCategory")))
                .andExpect(jsonPath("$.severity", is("MEDIUM")));
    }

    // ── 9. PUT /api/errors/{id} not found returns 404 ────────────────────────
    @Test
    void shouldReturn404WhenUpdatingNonExistentEntry() throws Exception {
        ErrorRequest request = buildRequest("pattern", "Category", "Cause", "Fix", "LOW");
        when(knowledgeBaseService.update(eq(999L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Known error not found"));

        mockMvc.perform(put("/api/errors/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ── 10. DELETE /api/errors/{id} returns 204 ───────────────────────────────
    @Test
    void shouldDeleteExistingEntry() throws Exception {
        doNothing().when(knowledgeBaseService).delete(1L);

        mockMvc.perform(delete("/api/errors/1"))
                .andExpect(status().isNoContent());
    }

    // ── 11. DELETE /api/errors/{id} not found returns 404 ────────────────────
    @Test
    void shouldReturn404WhenDeletingNonExistentEntry() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Known error not found"))
                .when(knowledgeBaseService).delete(404L);

        mockMvc.perform(delete("/api/errors/404"))
                .andExpect(status().isNotFound());
    }

    // ── 12. PUT /api/errors/{id} with non-numeric ID returns 400 ─────────────
    @Test
    void shouldReturn400ForNonNumericIdOnUpdate() throws Exception {
        ErrorRequest request = buildRequest("pattern", "Category", "Cause", "Fix", "LOW");

        mockMvc.perform(put("/api/errors/abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
