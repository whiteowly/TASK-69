package com.croh.events.dto;

import java.time.LocalDateTime;

public record EventResponse(
    Long id,
    String organizationId,
    String title,
    String description,
    String mode,
    String location,
    LocalDateTime startAt,
    LocalDateTime endAt,
    int capacity,
    boolean waitlistEnabled,
    boolean manualReviewRequired,
    String registrationFormSchema,
    String status,
    Long createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
