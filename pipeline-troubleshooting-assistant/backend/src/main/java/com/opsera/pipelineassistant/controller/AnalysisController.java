package com.opsera.pipelineassistant.controller;

import com.opsera.pipelineassistant.dto.AnalyzeRequest;
import com.opsera.pipelineassistant.model.AnalyzedLog;
import com.opsera.pipelineassistant.service.AnalysisService;
import com.opsera.pipelineassistant.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final DashboardService dashboardService;

    @PostMapping("/analyze")
    public AnalyzedLog analyze(@Valid @RequestBody AnalyzeRequest request) {
        return analysisService.analyze(request.getLogText());
    }

    @GetMapping("/history")
    public List<AnalyzedLog> getHistory() {
        return analysisService.getHistory();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        return dashboardService.getStats();
    }
}
