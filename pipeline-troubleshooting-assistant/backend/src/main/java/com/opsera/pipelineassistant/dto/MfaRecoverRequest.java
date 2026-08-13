package com.opsera.pipelineassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MfaRecoverRequest {

    @NotBlank(message = "Recovery code is required")
    private String recoveryCode;
}
