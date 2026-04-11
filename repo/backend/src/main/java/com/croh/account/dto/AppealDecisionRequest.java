package com.croh.account.dto;

import jakarta.validation.constraints.NotBlank;

public record AppealDecisionRequest(
    @NotBlank String decision,
    String decisionNote
) {}
