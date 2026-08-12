package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.AnalyzeRequest;
import com.opsera.pipelineassistant.dto.Responses.AnalysisResponse;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public List<AnalyzedLog> getHistory() {
        log.info("GET /api/history received");
        return analysisService.getHistory();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        log.info("GET /api/dashboard received");
        return dashboardService.getStats();
    }
}
