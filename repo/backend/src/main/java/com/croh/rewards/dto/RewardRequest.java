package com.croh.rewards.dto;

import jakarta.validation.constraints.NotBlank;

public record RewardRequest(
    @NotBlank String title,
    String description,
    String tier,
    Integer inventoryCount,
    Integer perUserLimit,
    String fulfillmentType,
    String status,
    String organizationId
) {}
