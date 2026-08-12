package com.opsera.pipelineassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnalyzeRequest {

    @NotBlank(message = "Log text is required")
    @Size(max = 100_000, message = "Log text exceeds maximum length of 100,000 characters")
    private String logText;
}
