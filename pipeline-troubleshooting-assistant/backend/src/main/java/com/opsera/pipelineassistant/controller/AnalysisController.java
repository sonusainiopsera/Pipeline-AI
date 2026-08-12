package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.AnalyzeRequest;
import com.opsera.pipelineassistant.dto.Responses.AnalysisResponse;
import com.opsera.pipelineassistant.dto.Responses.HistoryListDTO;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final AnalysisService analysisService;
    private final DashboardService dashboardService;

    @PostMapping("/analyze")
    public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        log.info("POST /api/analyze received");
        return AnalysisResponse.from(analysisService.analyze(request.getLogText()));
    }

    @GetMapping("/history")
    public Page<HistoryListDTO> getHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /api/history received");
        if (pageable.getPageSize() > 100) {
            pageable = PageRequest.of(pageable.getPageNumber(), 100, pageable.getSort());
        }
        return analysisService.getHistory(pageable);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        log.info("GET /api/dashboard received");
        return dashboardService.getStats();
    }
}
