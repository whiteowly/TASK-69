package com.croh.alerts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlertRuleDefaultRequest(
    @NotBlank String severity,
    @NotBlank String thresholdOperator,
    @NotNull Double thresholdValue,
    String thresholdUnit,
    Integer durationSeconds,
    Integer cooldownSeconds
) {}
