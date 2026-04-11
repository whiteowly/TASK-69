package com.croh.events.dto;

import jakarta.validation.constraints.NotBlank;

public record RegistrationDecisionRequest(
    @NotBlank String decision,
    String note
) {}
