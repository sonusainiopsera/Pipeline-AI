package com.opsera.pipelineassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsera.pipelineassistant.dto.Responses.HistoryListDTO;
import com.opsera.pipelineassistant.security.CustomUserDetailsService;
import com.opsera.pipelineassistant.security.JwtTokenProvider;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest tests for the GET /api/history pagination behaviour.
 * Covers default params, custom params, size clamping, empty results,
 * page wrapper structure, and payload size budget.
 */
@WebMvcTest(AnalysisController.class)
@WithMockUser(roles = "ANALYST")
class AnalysisControllerHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnalysisService analysisService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private HistoryListDTO makeDto(long id, String category) {
        return new HistoryListDTO(id, category, "root cause", "fix", "HIGH", 85,
                LocalDateTime.of(2025, 1, 15, 10, 0, 0));
    }

    // ── Default pagination ─────────────────────────────────────────────────────

    @Test
    void defaultPaginationUsesSize20AndPage0() throws Exception {
        when(analysisService.getHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(makeDto(1L, "Memory"))));

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(1)))   // PageImpl constructed with 1-item list
                .andExpect(jsonPath("$.number", is(0)));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(analysisService).getHistory(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    void defaultSortIsCreatedAtDesc() throws Exception {
        when(analysisService.getHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(analysisService).getHistory(captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // ── Custom page/size params ────────────────────────────────────────────────

    @Test
    void customPageAndSizeArePassedThrough() throws Exception {
        List<HistoryListDTO> items = List.of(makeDto(5L, "Network"), makeDto(6L, "Docker"));
        Page<HistoryListDTO> page = new PageImpl<>(items,
                PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "createdAt")), 35L);
        when(analysisService.getHistory(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/history").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.number", is(2)))
                .andExpect(jsonPath("$.size", is(10)))
                .andExpect(jsonPath("$.totalElements", is(35)))
                .andExpect(jsonPath("$.totalPages", is(4)));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(analysisService).getHistory(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    // ── Size clamping at 100 ───────────────────────────────────────────────────

    @Test
    void requestingSizeOver100IsClampedTo100() throws Exception {
        when(analysisService.getHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/history").param("size", "200"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(analysisService).getHistory(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void requestingExactly100IsNotClamped() throws Exception {
        when(analysisService.getHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/history").param("size", "100"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(analysisService).getHistory(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    // ── Empty results ──────────────────────────────────────────────────────────

    @Test
    void emptyDatabaseReturns200WithEmptyContent() throws Exception {
        Page<HistoryListDTO> empty = new PageImpl<>(List.of(),
                PageRequest.of(0, 20), 0L);
        when(analysisService.getHistory(any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)))
                .andExpect(jsonPath("$.empty", is(true)));
    }

    @Test
    void pageBeeyondLastReturnsEmptyContentWithCorrectMetadata() throws Exception {
        Page<HistoryListDTO> beyond = new PageImpl<>(List.of(),
                PageRequest.of(5, 20), 45L);
        when(analysisService.getHistory(any(Pageable.class))).thenReturn(beyond);

        mockMvc.perform(get("/api/history").param("page", "5").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(45)))
                .andExpect(jsonPath("$.totalPages", is(3)));
    }

    // ── Response structure ─────────────────────────────────────────────────────

    @Test
    void responseContainsRequiredPageWrapperFields() throws Exception {
        HistoryListDTO dto = makeDto(1L, "Memory");
        Page<HistoryListDTO> page = new PageImpl<>(List.of(dto),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 1L);
        when(analysisService.getHistory(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists())
                .andExpect(jsonPath("$.number").exists())
                .andExpect(jsonPath("$.size").exists())
                .andExpect(jsonPath("$.first").exists())
                .andExpect(jsonPath("$.last").exists())
                .andExpect(jsonPath("$.empty").exists());
    }

    @Test
    void contentItemContainsExpectedFieldsWithoutLogTextOrCustomerUpdate() throws Exception {
        HistoryListDTO dto = makeDto(7L, "Docker");
        when(analysisService.getHistory(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(7)))
                .andExpect(jsonPath("$.content[0].detectedCategory", is("Docker")))
                .andExpect(jsonPath("$.content[0].rootCause", is("root cause")))
                .andExpect(jsonPath("$.content[0].suggestedFix", is("fix")))
                .andExpect(jsonPath("$.content[0].severity", is("HIGH")))
                .andExpect(jsonPath("$.content[0].confidence", is(85)))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].logText").doesNotExist())
                .andExpect(jsonPath("$.content[0].customerUpdate").doesNotExist());
    }

    // ── Payload size budget ────────────────────────────────────────────────────

    @Test
    void payloadFor20ItemsIsUnder10KB() throws Exception {
        List<HistoryListDTO> items = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            items.add(new HistoryListDTO(
                    (long) i,
                    "Docker Issues",
                    "Container failed to start due to missing environment variable in compose file",
                    "Verify all required environment variables are set in docker-compose.yml",
                    "HIGH",
                    85,
                    LocalDateTime.of(2025, 1, 15, 10, i, 0)
            ));
        }
        Page<HistoryListDTO> page = new PageImpl<>(items,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")), 20L);
        when(analysisService.getHistory(any(Pageable.class))).thenReturn(page);

        MvcResult result = mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andReturn();

        byte[] responseBytes = result.getResponse().getContentAsByteArray();
        assertThat(responseBytes.length)
                .as("20-item history page payload should be under 10KB but was %d bytes", responseBytes.length)
                .isLessThan(10_240);
    }
}
