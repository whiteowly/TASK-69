package com.croh.auth.dto;

import java.util.List;
import java.util.Set;

public record LoginResponse(
    Long accountId,
    String username,
    List<String> approvedRoles,
    String activeRole,
    Set<String> permissions
) {
}
