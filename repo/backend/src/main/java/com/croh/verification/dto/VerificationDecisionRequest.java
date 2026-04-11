package com.croh.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record VerificationDecisionRequest(
    @NotBlank String decision,
    String reasonCode,
    String reviewNote
) {}
