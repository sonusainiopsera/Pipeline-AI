package com.opsera.pipelineassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MfaChallengeRequest {

    @NotBlank(message = "Authentication code is required")
    @Pattern(regexp = "\\d{6}", message = "Authentication code must be exactly 6 digits")
    private String code;
}
