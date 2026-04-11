package com.croh.rewards.dto;

import jakarta.validation.constraints.NotBlank;

public record TrackingRequest(
    @NotBlank String trackingNumber
) {}
