package com.croh.rewards.dto;

import jakarta.validation.constraints.NotNull;

public record OrderRequest(
    @NotNull Long rewardId,
    Integer quantity,
    String fulfillmentType,
    Long addressId
) {}
