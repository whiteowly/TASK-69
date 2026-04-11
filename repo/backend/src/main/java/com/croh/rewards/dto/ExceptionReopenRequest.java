package com.croh.rewards.dto;

import jakarta.validation.constraints.NotBlank;

public record ExceptionReopenRequest(
    @NotBlank String reasonCode,
    String note
) {}
