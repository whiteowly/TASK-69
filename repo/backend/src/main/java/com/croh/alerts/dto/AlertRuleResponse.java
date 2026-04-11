package com.croh.alerts.dto;

import java.time.LocalDateTime;

public record AlertRuleResponse(
    Long id,
    String alertType,
    String scopeType,
    String scopeId,
    String severity,
    String thresholdOperator,
    double thresholdValue,
    String thresholdUnit,
    int durationSeconds,
    int cooldownSeconds,
    LocalDateTime updatedAt
) {}
