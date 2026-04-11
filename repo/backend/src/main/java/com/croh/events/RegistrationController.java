package com.croh.events;

import com.croh.events.dto.RegistrationDecisionRequest;
import com.croh.events.dto.RegistrationResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private final EventService eventService;

    public RegistrationController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/pending")
    @RequirePermission(Permission.REVIEW_REGISTRATION)
    public ResponseEntity<List<RegistrationResponse>> getPendingRegistrations() {
        SessionAccount actor = getSessionAccount();
        List<RegistrationResponse> pending = eventService.getPendingRegistrations(
                actor.accountId(), actor.activeRole().name()).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/{id}/decision")
    @RequirePermission(Permission.REVIEW_REGISTRATION)
    public ResponseEntity<RegistrationResponse> decideRegistration(
            @PathVariable Long id,
            @Valid @RequestBody RegistrationDecisionRequest request) {
        SessionAccount actor = getSessionAccount();
        EventRegistration reg = eventService.decideRegistration(id, request.decision(), request.note(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(reg));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<RegistrationResponse> cancelRegistration(@PathVariable Long id) {
        SessionAccount actor = getSessionAccount();
        EventRegistration reg = eventService.cancelRegistration(id, actor.accountId());
        return ResponseEntity.ok(toResponse(reg));
    }

    private RegistrationResponse toResponse(EventRegistration r) {
        return new RegistrationResponse(r.getId(), r.getEventId(), r.getAccountId(), r.getStatus(),
                r.getFormResponses(), r.getReviewNote(), r.getReviewedBy(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
