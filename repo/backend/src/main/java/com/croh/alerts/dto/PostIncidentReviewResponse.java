package com.croh.alerts.dto;

import java.time.LocalDateTime;

public record PostIncidentReviewResponse(
    Long id,
    Long workOrderId,
    String summary,
    String lessonsLearned,
    String correctiveActions,
    Long reviewedBy,
    LocalDateTime createdAt
) {}
