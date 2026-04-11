package com.croh.account.dto;

import java.time.Instant;

public record RoleMembershipResponse(
    Long id,
    String roleType,
    String scopeId,
    String status,
    Instant createdAt
) {}
