package com.croh.account;

import com.croh.account.dto.RoleDecisionRequest;
import com.croh.account.dto.RoleMembershipResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
public class AdminRoleController {

    private final RoleService roleService;

    public AdminRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/pending")
    @RequirePermission(Permission.MANAGE_ROLE_APPROVALS)
    public ResponseEntity<List<RoleMembershipResponse>> listPendingRequests() {
        List<RoleMembership> pending = roleService.listPendingRequests();

        List<RoleMembershipResponse> responses = pending.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{membershipId}/decision")
    @RequirePermission(Permission.MANAGE_ROLE_APPROVALS)
    public ResponseEntity<RoleMembershipResponse> decideRole(
            @PathVariable Long membershipId,
            @Valid @RequestBody RoleDecisionRequest request) {
        SessionAccount actor = getSessionAccount();

        RoleMembership membership = roleService.decideRole(
                membershipId,
                request.decision(),
                request.reviewNote(),
                actor.accountId(),
                actor.activeRole().name()
        );

        return ResponseEntity.ok(toResponse(membership));
    }

    private RoleMembershipResponse toResponse(RoleMembership membership) {
        return new RoleMembershipResponse(
                membership.getId(),
                membership.getRoleType(),
                membership.getScopeId(),
                membership.getStatus().name(),
                membership.getCreatedAt().atZone(ZoneOffset.UTC).toInstant()
        );
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
