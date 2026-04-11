package com.croh.rewards.dto;

import java.time.LocalDateTime;

public record ExceptionResponse(
    Long id,
    Long orderId,
    String reasonCode,
    String description,
    String status,
    boolean supervisorApproval,
    String reopenReason,
    Long createdBy,
    Long resolvedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
