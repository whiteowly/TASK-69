package com.croh.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventRequest(
    @NotBlank String organizationId,
    @NotBlank String title,
    String description,
    String mode,
    String location,
    @NotNull LocalDateTime startAt,
    @NotNull LocalDateTime endAt,
    Integer capacity,
    Boolean waitlistEnabled,
    Boolean manualReviewRequired,
    String registrationFormSchema,
    String status
) {}
