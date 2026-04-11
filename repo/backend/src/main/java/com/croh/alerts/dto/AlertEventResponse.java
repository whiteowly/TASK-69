package com.croh.alerts.dto;

import java.time.LocalDateTime;

public record AlertEventResponse(
    Long id,
    String alertType,
    String scopeType,
    String scopeId,
    String severity,
    double measuredValue,
    String measuredUnit,
    boolean suppressed,
    Long workOrderId,
    boolean durationSatisfied,
    Long durationStreakSeconds,
    Integer durationRequiredSeconds,
    LocalDateTime createdAt
) {}
