package com.croh.alerts.dto;

import jakarta.validation.constraints.NotBlank;

public record PostIncidentReviewRequest(
    @NotBlank String summary,
    String lessons,
    String actions
) {}
