package com.opsera.pipelineassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ErrorRequest {

    @NotBlank(message = "Error pattern is required")
    private String errorPattern;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Root cause is required")
    private String rootCause;

    @NotBlank(message = "Solution is required")
    private String solution;

    @NotBlank(message = "Severity is required")
    private String severity;
}
