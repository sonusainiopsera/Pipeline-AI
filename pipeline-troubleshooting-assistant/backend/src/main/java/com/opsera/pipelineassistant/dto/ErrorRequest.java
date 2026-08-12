package com.opsera.pipelineassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ErrorRequest {

    @NotBlank(message = "Error pattern is required")
    @Size(max = 5_000, message = "Error pattern exceeds maximum length of 5,000 characters")
    private String errorPattern;

    @NotBlank(message = "Category is required")
    @Size(max = 80, message = "Category exceeds maximum length of 80 characters")
    private String category;

    @NotBlank(message = "Root cause is required")
    @Size(max = 5_000, message = "Root cause exceeds maximum length of 5,000 characters")
    private String rootCause;

    @NotBlank(message = "Solution is required")
    @Size(max = 5_000, message = "Solution exceeds maximum length of 5,000 characters")
    private String solution;

    @NotBlank(message = "Severity is required")
    @Size(max = 20, message = "Severity exceeds maximum length of 20 characters")
    private String severity;
}
