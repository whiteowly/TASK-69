package com.croh.rewards.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderTransitionRequest(
    @NotBlank String toState,
    String note
) {}
