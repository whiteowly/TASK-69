package com.croh.events.dto;

import java.time.LocalDateTime;

public record RegistrationResponse(
    Long id,
    Long eventId,
    Long accountId,
    String status,
    String formResponses,
    String reviewNote,
    Long reviewedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
