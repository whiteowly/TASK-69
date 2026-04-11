package com.croh.rewards.dto;

import java.time.LocalDateTime;

public record RewardResponse(
    Long id,
    String title,
    String description,
    String tier,
    int inventoryCount,
    int perUserLimit,
    String fulfillmentType,
    String status,
    Long createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
