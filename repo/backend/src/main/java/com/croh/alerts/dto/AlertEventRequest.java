package com.croh.alerts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlertEventRequest(
    @NotBlank String alertType,
    String scopeType,
    String scopeId,
    @NotNull Double measuredValue,
    String unit
) {}
