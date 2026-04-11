package com.croh.account.dto;

import java.time.Instant;

public record BlacklistResponse(
    Long blacklistId,
    Long accountId,
    String reasonCode,
    Instant createdAt
) {}
