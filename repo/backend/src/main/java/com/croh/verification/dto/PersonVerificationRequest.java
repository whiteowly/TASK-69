package com.croh.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record PersonVerificationRequest(
    @NotBlank String legalName,
    @NotBlank String dateOfBirth
) {}
