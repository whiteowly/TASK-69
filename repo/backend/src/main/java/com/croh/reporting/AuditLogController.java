package com.croh.reporting;

import com.croh.audit.AuditLog;
import com.croh.audit.AuditLogRepository;
import com.croh.common.PagedResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @RequirePermission(Permission.VIEW_AUDIT_LOGS)
    public ResponseEntity<PagedResponse<AuditLog>> getAuditLogs(
            @RequestParam(required = false) Long actorAccountId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

        Page<AuditLog> result;

        if (actorAccountId != null && actionType != null) {
            result = auditLogRepository.findByActorAccountIdAndActionType(
                    actorAccountId, actionType, pageRequest);
        } else if (actorAccountId != null) {
            result = auditLogRepository.findByActorAccountId(actorAccountId, pageRequest);
        } else if (actionType != null) {
            result = auditLogRepository.findByActionType(actionType, pageRequest);
        } else if (objectType != null) {
            result = auditLogRepository.findByObjectType(objectType, pageRequest);
        } else if (from != null && to != null) {
            result = auditLogRepository.findByTimestampBetween(from, to, pageRequest);
        } else {
            result = auditLogRepository.findAll(pageRequest);
        }

        return ResponseEntity.ok(new PagedResponse<>(
                result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements()));
    }
}
