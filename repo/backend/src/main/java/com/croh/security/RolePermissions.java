package com.croh.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RolePermissions {

    private static final Map<RoleType, Set<Permission>> ROLE_PERMISSION_MAP;

    static {
        Map<RoleType, Set<Permission>> map = new EnumMap<>(RoleType.class);

        map.put(RoleType.ADMIN, Collections.unmodifiableSet(EnumSet.allOf(Permission.class)));

        map.put(RoleType.ORG_OPERATOR, Collections.unmodifiableSet(EnumSet.of(
                Permission.PUBLISH_EVENT,
                Permission.REVIEW_REGISTRATION,
                Permission.PUBLISH_RESOURCE,
                Permission.MANAGE_RESOURCE_POLICY,
                Permission.MANAGE_REWARDS,
                Permission.MANAGE_REWARD_FULFILLMENT,
                Permission.CONFIGURE_ALERT_RULES,
                Permission.EXPORT_REPORTS
        )));

        map.put(RoleType.VOLUNTEER, Collections.unmodifiableSet(EnumSet.of(
                Permission.REVIEW_REGISTRATION,
                Permission.REVIEW_VERIFICATION
        )));

        map.put(RoleType.PARTICIPANT, Collections.unmodifiableSet(EnumSet.of(
                Permission.SELF_SERVICE
        )));

        ROLE_PERMISSION_MAP = Collections.unmodifiableMap(map);
    }

    private RolePermissions() {
        // utility class
    }

    public static Set<Permission> getPermissions(RoleType roleType) {
        return ROLE_PERMISSION_MAP.getOrDefault(roleType, Collections.emptySet());
    }
}
