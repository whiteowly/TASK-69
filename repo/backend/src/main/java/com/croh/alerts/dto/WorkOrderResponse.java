package com.croh.alerts.dto;

import java.time.LocalDateTime;

public record WorkOrderResponse(
    Long id,
    Long alertEventId,
    String title,
    String description,
    String severity,
    String status,
    Long assignedTo,
    LocalDateTime firstResponseAt,
    LocalDateTime closedAt,
    Long createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long firstResponseSeconds,
    Long timeToCloseSeconds
) {}
