package com.croh.resources;

import com.croh.resources.dto.PolicyRequest;
import com.croh.resources.dto.PolicyResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resource-policies")
public class PolicyController {

    private final ResourceService resourceService;

    public PolicyController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @RequirePermission(Permission.MANAGE_RESOURCE_POLICY)
    public ResponseEntity<List<PolicyResponse>> listPolicies() {
        List<PolicyResponse> policies = resourceService.listPolicies().stream()
                .map(p -> new PolicyResponse(p.getId(), p.getName(), p.getScope(),
                        p.getMaxActions(), p.getWindowDays(), p.getResourceAction(),
                        p.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(policies);
    }

    @PostMapping
    @RequirePermission(Permission.MANAGE_RESOURCE_POLICY)
    public ResponseEntity<PolicyResponse> createPolicy(@Valid @RequestBody PolicyRequest request) {
        SessionAccount actor = getSessionAccount();
        UsagePolicy policy = resourceService.createPolicy(
                request.name(), request.scope(), request.maxActions(),
                request.windowDays(), request.resourceAction(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.status(201).body(new PolicyResponse(
                policy.getId(), policy.getName(), policy.getScope(),
                policy.getMaxActions(), policy.getWindowDays(),
                policy.getResourceAction(), policy.getCreatedAt()));
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
