package com.croh.rewards.dto;

import jakarta.validation.constraints.NotBlank;

public record ExceptionTransitionRequest(
    @NotBlank String toState
) {}
