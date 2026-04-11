package com.croh.rewards.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExceptionRequest(
    @NotNull Long orderId,
    @NotBlank String reasonCode,
    String description
) {}
