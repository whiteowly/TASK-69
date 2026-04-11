package com.croh.alerts;

import com.croh.alerts.dto.AlertRuleDefaultRequest;
import com.croh.alerts.dto.AlertRuleResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts/rules")
public class AlertRuleController {

    private final AlertService alertService;

    public AlertRuleController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<Map<String, List<?>>> getRules() {
        return ResponseEntity.ok(alertService.getRules());
    }

    @PutMapping("/defaults/{alertType}")
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<AlertRuleResponse> updateDefault(
            @PathVariable String alertType,
            @Valid @RequestBody AlertRuleDefaultRequest request) {
        SessionAccount actor = getSessionAccount();
        AlertRuleDefault rule = alertService.updateDefault(
                alertType, request.severity(), request.thresholdOperator(),
                request.thresholdValue(), request.thresholdUnit(),
                request.durationSeconds() != null ? request.durationSeconds() : 0,
                request.cooldownSeconds() != null ? request.cooldownSeconds() : 900,
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(rule));
    }

    @PutMapping("/overrides/{scopeType}/{scopeId}/{alertType}")
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<AlertRuleResponse> updateOverride(
            @PathVariable String scopeType,
            @PathVariable String scopeId,
            @PathVariable String alertType,
            @Valid @RequestBody AlertRuleDefaultRequest request) {
        SessionAccount actor = getSessionAccount();
        AlertRuleOverride rule = alertService.updateOverride(
                alertType, scopeType, scopeId,
                request.severity(), request.thresholdOperator(),
                request.thresholdValue(), request.thresholdUnit(),
                request.durationSeconds() != null ? request.durationSeconds() : 0,
                request.cooldownSeconds() != null ? request.cooldownSeconds() : 900,
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(new AlertRuleResponse(
                rule.getId(), rule.getAlertType(), rule.getScopeType(), rule.getScopeId(),
                rule.getSeverity(), rule.getThresholdOperator(), rule.getThresholdValue(),
                rule.getThresholdUnit(), rule.getDurationSeconds(), rule.getCooldownSeconds(),
                rule.getUpdatedAt()));
    }

    private AlertRuleResponse toResponse(AlertRuleDefault r) {
        return new AlertRuleResponse(r.getId(), r.getAlertType(), null, null,
                r.getSeverity(), r.getThresholdOperator(), r.getThresholdValue(),
                r.getThresholdUnit(), r.getDurationSeconds(), r.getCooldownSeconds(),
                r.getUpdatedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
