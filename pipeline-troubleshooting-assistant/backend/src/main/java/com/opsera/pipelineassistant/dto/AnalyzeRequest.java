package com.opsera.pipelineassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnalyzeRequest {

    @NotBlank(message = "Log text is required")
    private String logText;
}
