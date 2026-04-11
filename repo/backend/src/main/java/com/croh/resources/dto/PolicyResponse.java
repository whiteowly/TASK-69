package com.croh.resources.dto;

import java.time.LocalDateTime;

public record PolicyResponse(
    Long id,
    String name,
    String scope,
    int maxActions,
    int windowDays,
    String resourceAction,
    LocalDateTime createdAt
) {}
