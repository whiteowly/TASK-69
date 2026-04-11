package com.croh.alerts.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkOrderRequest(
    @NotBlank String title,
    String description,
    String severity,
    Long alertEventId,
    String organizationId
) {}
