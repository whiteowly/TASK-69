package com.croh.security;

import java.io.Serializable;
import java.util.Set;

public record SessionAccount(
    Long accountId,
    String username,
    RoleType activeRole,
    Set<Permission> permissions,
    String accountStatus
) implements Serializable {

    public boolean hasPermission(Permission permission) {
        return permissions != null && permissions.contains(permission);
    }
}
