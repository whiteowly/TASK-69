package com.croh.account;

import com.croh.account.dto.RoleMembershipResponse;
import com.croh.account.dto.RoleRequestDto;
import com.croh.account.dto.RoleSwitchRequest;
import com.croh.security.SessionAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/accounts/me")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("/role-requests")
    public ResponseEntity<RoleMembershipResponse> requestRole(
            @Valid @RequestBody RoleRequestDto request) {
        SessionAccount actor = getSessionAccount();

        RoleMembership membership = roleService.requestRole(
                actor.accountId(),
                request.role(),
                request.scopeType(),
                request.scopeId()
        );

        RoleMembershipResponse response = toResponse(membership);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/roles")
    public ResponseEntity<List<RoleMembershipResponse>> listRoles() {
        SessionAccount actor = getSessionAccount();
        List<RoleMembership> memberships = roleService.listRoles(actor.accountId());

        List<RoleMembershipResponse> responses = memberships.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/active-role")
    public ResponseEntity<Map<String, Object>> switchActiveRole(
            @Valid @RequestBody RoleSwitchRequest request,
            HttpServletRequest httpRequest) {
        SessionAccount actor = getSessionAccount();

        SessionAccount newSession = roleService.switchRole(
                actor.accountId(),
                request.role(),
                request.scopeId(),
                actor,
                httpRequest.getSession()
        );

        Map<String, Object> response = Map.of(
                "accountId", newSession.accountId(),
                "username", newSession.username(),
                "activeRole", newSession.activeRole().name(),
                "permissions", newSession.permissions().stream()
                        .map(Enum::name)
                        .toList()
        );
        return ResponseEntity.ok(response);
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
