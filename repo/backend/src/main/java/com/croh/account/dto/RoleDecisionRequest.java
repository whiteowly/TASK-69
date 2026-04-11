package com.croh.account.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleDecisionRequest(
    @NotBlank String decision,
    String reviewNote
) {}
