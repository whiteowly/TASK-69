package com.croh.rewards.dto;

import java.time.LocalDateTime;

public record OrderResponse(
    Long id,
    Long rewardId,
    Long accountId,
    int quantity,
    String fulfillmentType,
    Long shippingAddressId,
    String status,
    String trackingNumber,
    String voucherCode,
    String note,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
