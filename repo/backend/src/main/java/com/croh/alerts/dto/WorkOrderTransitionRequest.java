package com.croh.alerts.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkOrderTransitionRequest(
    @NotBlank String toStatus
) {}
