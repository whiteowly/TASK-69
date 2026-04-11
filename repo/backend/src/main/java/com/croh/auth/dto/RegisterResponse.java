package com.croh.auth.dto;

import java.time.Instant;

public record RegisterResponse(
    Long accountId,
    String username,
    String status,
    Instant createdAt
) {}
