package com.croh.alerts;

import com.croh.alerts.dto.NoteRequest;
import com.croh.alerts.dto.PostIncidentReviewRequest;
import com.croh.alerts.dto.PostIncidentReviewResponse;
import com.croh.alerts.dto.WorkOrderRequest;
import com.croh.alerts.dto.WorkOrderResponse;
import com.croh.alerts.dto.WorkOrderTransitionRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<WorkOrderResponse> createWorkOrder(
            @Valid @RequestBody WorkOrderRequest request) {
        SessionAccount actor = getSessionAccount();
        WorkOrder wo = workOrderService.createWorkOrder(
                request.title(), request.description(), request.severity(),
                request.alertEventId(), request.organizationId(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.status(201).body(toResponse(wo));
    }

    @GetMapping
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<List<WorkOrderResponse>> listWorkOrders(
            @RequestParam(required = false) String status) {
        SessionAccount actor = getSessionAccount();
        boolean isAdmin = actor.activeRole() == com.croh.security.RoleType.ADMIN;
        List<WorkOrderResponse> orders = workOrderService.listWorkOrders(status, actor.accountId(), isAdmin).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<WorkOrderResponse> getWorkOrder(@PathVariable Long id) {
        SessionAccount actor = getSessionAccount();
        WorkOrder wo = workOrderService.getWorkOrder(id, actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(wo));
    }

    @PostMapping("/{id}/transition")
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<WorkOrderResponse> transitionWorkOrder(
            @PathVariable Long id,
            @Valid @RequestBody WorkOrderTransitionRequest request) {
        SessionAccount actor = getSessionAccount();
        WorkOrder wo = workOrderService.transitionWorkOrder(id, request.toStatus(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(wo));
    }

    @PostMapping("/{id}/assign")
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<WorkOrderResponse> assignWorkOrder(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        SessionAccount actor = getSessionAccount();
        Long assigneeId = body.get("assigneeId");
        WorkOrder wo = workOrderService.assignWorkOrder(id, assigneeId,
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(wo));
    }

    @PostMapping("/{id}/notes")
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<Map<String, Object>> addNote(
            @PathVariable Long id,
            @Valid @RequestBody NoteRequest request) {
        SessionAccount actor = getSessionAccount();
        WorkOrderNote note = workOrderService.addNote(id, actor.accountId(),
                actor.activeRole().name(), request.content());
        return ResponseEntity.status(201).body(Map.of(
                "id", note.getId(),
                "workOrderId", note.getWorkOrderId(),
                "authorId", note.getAuthorId(),
                "content", note.getContent(),
                "createdAt", note.getCreatedAt().toString()
        ));
    }

    @PostMapping("/{id}/photos")
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<Map<String, Object>> addPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        SessionAccount actor = getSessionAccount();
        WorkOrderPhoto photo = workOrderService.addPhoto(
                id, actor.accountId(), actor.activeRole().name(),
                file.getBytes(), file.getOriginalFilename(), file.getContentType());
        return ResponseEntity.status(201).body(Map.of(
                "id", photo.getId(),
                "workOrderId", photo.getWorkOrderId(),
                "fileName", photo.getFileName(),
                "contentType", photo.getContentType(),
                "createdAt", photo.getCreatedAt().toString()
        ));
    }

    @PostMapping("/{id}/post-incident-review")
    @RequirePermission(Permission.CONFIGURE_ALERT_RULES)
    public ResponseEntity<PostIncidentReviewResponse> createPostIncidentReview(
            @PathVariable Long id,
            @Valid @RequestBody PostIncidentReviewRequest request) {
        SessionAccount actor = getSessionAccount();
        PostIncidentReview pir = workOrderService.createPostIncidentReview(
                id, request.summary(), request.lessons(), request.actions(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.status(201).body(new PostIncidentReviewResponse(
                pir.getId(), pir.getWorkOrderId(), pir.getSummary(),
                pir.getLessonsLearned(), pir.getCorrectiveActions(),
                pir.getReviewedBy(), pir.getCreatedAt()));
    }

    private WorkOrderResponse toResponse(WorkOrder wo) {
        return new WorkOrderResponse(wo.getId(), wo.getAlertEventId(), wo.getTitle(),
                wo.getDescription(), wo.getSeverity(), wo.getStatus(), wo.getAssignedTo(),
                wo.getFirstResponseAt(), wo.getClosedAt(), wo.getCreatedBy(),
                wo.getCreatedAt(), wo.getUpdatedAt(),
                workOrderService.computeFirstResponseSeconds(wo),
                workOrderService.computeTimeToCloseSeconds(wo));
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
