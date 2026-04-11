package com.croh.rewards.dto;

import java.time.LocalDateTime;

public record AddressResponse(
    Long id,
    Long accountId,
    String city,
    String stateCode,
    String zipCode,
    boolean primary,
    LocalDateTime createdAt
) {}
