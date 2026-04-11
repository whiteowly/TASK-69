package com.croh.alerts;

import com.croh.audit.AuditService;
import com.croh.files.FileStorageService;
import com.croh.security.OrgScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkOrderService {

    private static final Map<String, Set<String>> WO_TRANSITIONS = Map.of(
            "NEW_ALERT", Set.of("ACKNOWLEDGED"),
            "ACKNOWLEDGED", Set.of("DISPATCHED"),
            "DISPATCHED", Set.of("IN_PROGRESS"),
            "IN_PROGRESS", Set.of("RESOLVED"),
            "RESOLVED", Set.of("CLOSED")
    );

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderNoteRepository noteRepository;
    private final WorkOrderPhotoRepository photoRepository;
    private final PostIncidentReviewRepository pirRepository;
    private final FileStorageService fileStorageService;
    private final OrgScopeService orgScopeService;
    private final AuditService auditService;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            WorkOrderNoteRepository noteRepository,
                            WorkOrderPhotoRepository photoRepository,
                            PostIncidentReviewRepository pirRepository,
                            FileStorageService fileStorageService,
                            OrgScopeService orgScopeService,
                            AuditService auditService) {
        this.workOrderRepository = workOrderRepository;
        this.noteRepository = noteRepository;
        this.photoRepository = photoRepository;
        this.pirRepository = pirRepository;
        this.fileStorageService = fileStorageService;
        this.orgScopeService = orgScopeService;
        this.auditService = auditService;
    }

    /**
     * Enforces that the actor is authorized to access the given work order,
     * based on the work order's own organizationId (not inferred from creator).
     */
    private void enforceWorkOrderScope(WorkOrder wo, Long actorId, String actorRole) {
        orgScopeService.enforceOrgScope(actorId, actorRole, wo.getOrganizationId());
    }

    @Transactional
    public WorkOrder createWorkOrder(String title, String description, String severity,
                                      Long alertEventId, String organizationId,
                                      Long actorId, String actorRole) {
        orgScopeService.enforceOrgScope(actorId, actorRole, organizationId);

        WorkOrder wo = new WorkOrder();
        wo.setTitle(title);
        wo.setDescription(description);
        wo.setOrganizationId(organizationId);
        wo.setSeverity(severity != null ? severity : "MEDIUM");
        wo.setAlertEventId(alertEventId);
        wo.setStatus("NEW_ALERT");
        wo.setCreatedBy(actorId);
        wo.setCreatedAt(LocalDateTime.now());
        wo.setUpdatedAt(LocalDateTime.now());

        WorkOrder saved = workOrderRepository.save(wo);

        auditService.log(actorId, null, "WORK_ORDER_CREATED",
                "WorkOrder", saved.getId().toString(),
                null, "NEW_ALERT", null, null);

        return saved;
    }

    @Transactional
    public WorkOrder transitionWorkOrder(Long id, String toStatus, Long actorId, String actorRole) {
        WorkOrder wo = workOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Work order not found: " + id));
        enforceWorkOrderScope(wo, actorId, actorRole);

        String beforeStatus = wo.getStatus();
        Set<String> allowedNext = WO_TRANSITIONS.get(beforeStatus);
        if (allowedNext == null || !allowedNext.contains(toStatus)) {
            throw new IllegalStateException(
                    "Invalid transition from " + beforeStatus + " to " + toStatus);
        }

        wo.setStatus(toStatus);
        wo.setUpdatedAt(LocalDateTime.now());

        // Record first_response_at on first ACKNOWLEDGED
        if ("ACKNOWLEDGED".equals(toStatus) && wo.getFirstResponseAt() == null) {
            wo.setFirstResponseAt(LocalDateTime.now());
        }

        // Record closed_at on CLOSED
        if ("CLOSED".equals(toStatus)) {
            wo.setClosedAt(LocalDateTime.now());
        }

        WorkOrder saved = workOrderRepository.save(wo);

        auditService.log(actorId, null, "WORK_ORDER_TRANSITIONED",
                "WorkOrder", saved.getId().toString(),
                beforeStatus, toStatus, null, null);

        return saved;
    }

    @Transactional
    public WorkOrder assignWorkOrder(Long id, Long assigneeId, Long actorId, String actorRole) {
        WorkOrder wo = workOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Work order not found: " + id));
        enforceWorkOrderScope(wo, actorId, actorRole);

        if (!"DISPATCHED".equals(wo.getStatus())) {
            throw new IllegalStateException("Can only assign work orders in DISPATCHED status");
        }

        wo.setAssignedTo(assigneeId);
        wo.setUpdatedAt(LocalDateTime.now());

        WorkOrder saved = workOrderRepository.save(wo);

        auditService.log(actorId, null, "WORK_ORDER_ASSIGNED",
                "WorkOrder", saved.getId().toString(),
                null, null, null, null);

        return saved;
    }

    @Transactional
    public WorkOrderNote addNote(Long workOrderId, Long authorId, String actorRole, String content) {
        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Work order not found: " + workOrderId));
        enforceWorkOrderScope(wo, authorId, actorRole);

        WorkOrderNote note = new WorkOrderNote();
        note.setWorkOrderId(workOrderId);
        note.setAuthorId(authorId);
        note.setContent(content);
        note.setCreatedAt(LocalDateTime.now());

        return noteRepository.save(note);
    }

    @Transactional
    public WorkOrderPhoto addPhoto(Long workOrderId, Long uploaderId, String actorRole,
                                    byte[] fileBytes, String fileName, String contentType) {
        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Work order not found: " + workOrderId));
        enforceWorkOrderScope(wo, uploaderId, actorRole);

        String filePath = fileStorageService.store(fileBytes, "work-order-photos");

        WorkOrderPhoto photo = new WorkOrderPhoto();
        photo.setWorkOrderId(workOrderId);
        photo.setFilePath(filePath);
        photo.setFileName(fileName);
        photo.setContentType(contentType);
        photo.setUploadedBy(uploaderId);
        photo.setCreatedAt(LocalDateTime.now());

        return photoRepository.save(photo);
    }

    @Transactional
    public PostIncidentReview createPostIncidentReview(Long workOrderId, String summary,
                                                        String lessons, String actions,
                                                        Long actorId, String actorRole) {
        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Work order not found: " + workOrderId));
        enforceWorkOrderScope(wo, actorId, actorRole);

        if (pirRepository.findByWorkOrderId(workOrderId).isPresent()) {
            throw new IllegalStateException("Post-incident review already exists for work order: " + workOrderId);
        }

        PostIncidentReview pir = new PostIncidentReview();
        pir.setWorkOrderId(workOrderId);
        pir.setSummary(summary);
        pir.setLessonsLearned(lessons);
        pir.setCorrectiveActions(actions);
        pir.setReviewedBy(actorId);
        pir.setCreatedAt(LocalDateTime.now());

        PostIncidentReview saved = pirRepository.save(pir);

        auditService.log(actorId, null, "POST_INCIDENT_REVIEW_CREATED",
                "PostIncidentReview", saved.getId().toString(),
                null, null, null, null);

        return saved;
    }

    public WorkOrder getWorkOrder(Long id, Long actorId, String actorRole) {
        WorkOrder wo = workOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Work order not found: " + id));
        enforceWorkOrderScope(wo, actorId, actorRole);
        return wo;
    }

    public Long computeFirstResponseSeconds(WorkOrder wo) {
        if (wo.getFirstResponseAt() == null || wo.getCreatedAt() == null) return null;
        return Duration.between(wo.getCreatedAt(), wo.getFirstResponseAt()).getSeconds();
    }

    public Long computeTimeToCloseSeconds(WorkOrder wo) {
        if (wo.getClosedAt() == null || wo.getCreatedAt() == null) return null;
        return Duration.between(wo.getCreatedAt(), wo.getClosedAt()).getSeconds();
    }

    public List<WorkOrder> listWorkOrders(String statusFilter, Long actorId, boolean isAdmin) {
        if (isAdmin) {
            if (statusFilter != null && !statusFilter.isBlank()) {
                return workOrderRepository.findByStatus(statusFilter);
            }
            return workOrderRepository.findAll();
        }
        // ORG_OPERATOR: scoped to work orders bound to actor's org scopes
        List<String> orgIds = orgScopeService.getOrgScopes(actorId);
        if (orgIds.isEmpty()) return List.of();
        if (statusFilter != null && !statusFilter.isBlank()) {
            return workOrderRepository.findByOrganizationIdInAndStatus(orgIds, statusFilter);
        }
        return workOrderRepository.findByOrganizationIdIn(orgIds);
    }
}
