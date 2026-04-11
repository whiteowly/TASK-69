package com.croh.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AuditLog log(Long actorId, String actorRole, String actionType,
                        String objectType, String objectId,
                        String beforeState, String afterState,
                        String reasonCode, String correlationId) {
        AuditLog entry = new AuditLog();
        entry.setEventId(UUID.randomUUID().toString());
        entry.setTimestamp(LocalDateTime.now());
        entry.setActorAccountId(actorId);
        entry.setActorRole(actorRole);
        entry.setActionType(actionType);
        entry.setObjectType(objectType);
        entry.setObjectId(objectId);
        entry.setBeforeState(beforeState);
        entry.setAfterState(afterState);
        entry.setReasonCode(reasonCode);
        entry.setCorrelationId(correlationId);
        return auditLogRepository.save(entry);
    }
}
