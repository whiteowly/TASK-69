package com.croh.alerts;

import com.croh.alerts.dto.AlertEventRequest;
import com.croh.alerts.dto.AlertEventResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts/events")
public class AlertEventController {

    private final AlertService alertService;

    public AlertEventController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<AlertEventResponse> ingestEvent(
            @Valid @RequestBody AlertEventRequest request) {
        AlertService.AlertEventResult result = alertService.ingestEvent(
                request.alertType(), request.scopeType(), request.scopeId(),
                request.measuredValue(), request.unit());
        AlertEvent event = result.alertEvent();
        Long workOrderId = result.workOrder() != null ? result.workOrder().getId() : null;
        AlertService.DurationEvaluation dur = result.durationEvaluation();
        return ResponseEntity.status(201).body(new AlertEventResponse(
                event.getId(), event.getAlertType(), event.getScopeType(), event.getScopeId(),
                event.getSeverity(), event.getMeasuredValue(), event.getMeasuredUnit(),
                event.isSuppressed(), workOrderId,
                dur.satisfied(), dur.streakSeconds(), dur.requiredSeconds(),
                event.getCreatedAt()));
    }
}
