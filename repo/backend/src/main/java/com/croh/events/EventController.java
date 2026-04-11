package com.croh.events;

import com.croh.events.dto.EventRequest;
import com.croh.events.dto.EventResponse;
import com.croh.events.dto.RegistrationRequest;
import com.croh.events.dto.RegistrationResponse;
import com.croh.events.dto.RosterEntry;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @RequirePermission(Permission.PUBLISH_EVENT)
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        SessionAccount actor = getSessionAccount();
        Event event = eventService.createEvent(
                request.organizationId(), request.title(), request.description(),
                request.mode(), request.location(), request.startAt(), request.endAt(),
                request.capacity(), request.waitlistEnabled(), request.manualReviewRequired(),
                request.registrationFormSchema(), request.status(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.status(201).body(toEventResponse(event));
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> listEvents() {
        List<EventResponse> events = eventService.listEvents().stream()
                .map(this::toEventResponse)
                .toList();
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long id) {
        Event event = eventService.getEvent(id);
        return ResponseEntity.ok(toEventResponse(event));
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permission.PUBLISH_EVENT)
    public ResponseEntity<EventResponse> updateEvent(@PathVariable Long id,
                                                      @RequestBody EventRequest request) {
        SessionAccount actor = getSessionAccount();
        Event event = eventService.updateEvent(id,
                request.title(), request.description(), request.mode(),
                request.location(), request.startAt(), request.endAt(),
                request.capacity(), request.waitlistEnabled(), request.manualReviewRequired(),
                request.registrationFormSchema(), request.status(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toEventResponse(event));
    }

    @PostMapping("/{id}/registrations")
    public ResponseEntity<RegistrationResponse> register(@PathVariable Long id,
                                                          @RequestBody RegistrationRequest request) {
        SessionAccount actor = getSessionAccount();
        EventRegistration reg = eventService.register(id, actor.accountId(), request.formResponses());
        return ResponseEntity.status(201).body(toRegistrationResponse(reg));
    }

    @GetMapping("/{id}/roster")
    @RequirePermission(Permission.REVIEW_REGISTRATION)
    public ResponseEntity<List<RosterEntry>> getRoster(@PathVariable Long id) {
        List<RosterEntry> roster = eventService.getRoster(id).stream()
                .map(reg -> new RosterEntry(reg.getId(), reg.getAccountId(), reg.getStatus(), reg.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(roster);
    }

    @PostMapping("/{id}/roster/export")
    @RequirePermission(Permission.EXPORT_REPORTS)
    public ResponseEntity<java.util.Map<String, String>> exportRoster(@PathVariable Long id,
                                               @RequestParam(defaultValue = "CSV") String format) {
        SessionAccount actor = getSessionAccount();
        String exportPath = eventService.exportRoster(id, format, actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(java.util.Map.of("exportFilePath", exportPath, "format", format));
    }

    private EventResponse toEventResponse(Event e) {
        return new EventResponse(e.getId(), e.getOrganizationId(), e.getTitle(), e.getDescription(),
                e.getMode(), e.getLocation(), e.getStartAt(), e.getEndAt(), e.getCapacity(),
                e.isWaitlistEnabled(), e.isManualReviewRequired(), e.getRegistrationFormSchema(),
                e.getStatus(), e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private RegistrationResponse toRegistrationResponse(EventRegistration r) {
        return new RegistrationResponse(r.getId(), r.getEventId(), r.getAccountId(), r.getStatus(),
                r.getFormResponses(), r.getReviewNote(), r.getReviewedBy(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
