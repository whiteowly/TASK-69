package com.croh.events;

import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
import com.croh.audit.AuditService;
import com.croh.reporting.ExportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final RoleMembershipRepository roleMembershipRepository;
    private final AuditService auditService;
    private final ExportService exportService;

    public EventService(EventRepository eventRepository,
                        EventRegistrationRepository registrationRepository,
                        RoleMembershipRepository roleMembershipRepository,
                        AuditService auditService,
                        ExportService exportService) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.roleMembershipRepository = roleMembershipRepository;
        this.auditService = auditService;
        this.exportService = exportService;
    }

    @Transactional
    public Event createEvent(String organizationId, String title, String description,
                             String mode, String location, LocalDateTime startAt, LocalDateTime endAt,
                             Integer capacity, Boolean waitlistEnabled, Boolean manualReviewRequired,
                             String registrationFormSchema, String status, Long actorId, String actorRole) {
        // Validate org scope: actor must have approved ORG_OPERATOR membership for this org
        validateOrganizationScope(actorId, organizationId, actorRole);

        Event event = new Event();
        event.setOrganizationId(organizationId);
        event.setTitle(title);
        event.setDescription(description);
        event.setMode(mode != null ? mode : "ON_SITE");
        event.setLocation(location);
        event.setStartAt(startAt);
        event.setEndAt(endAt);
        event.setCapacity(capacity != null ? capacity : 50);
        event.setWaitlistEnabled(waitlistEnabled != null ? waitlistEnabled : false);
        event.setManualReviewRequired(manualReviewRequired != null ? manualReviewRequired : false);
        event.setRegistrationFormSchema(registrationFormSchema);
        event.setStatus(status != null ? status : "DRAFT");
        event.setCreatedBy(actorId);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());

        Event saved = eventRepository.save(event);

        auditService.log(actorId, actorRole, "EVENT_CREATED",
                "Event", saved.getId().toString(),
                null, saved.getStatus(), null, null);

        return saved;
    }

    public List<Event> listEvents() {
        return eventRepository.findByStatus("PUBLISHED");
    }

    public Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
    }

    @Transactional
    public Event updateEvent(Long id, String title, String description, String mode,
                             String location, LocalDateTime startAt, LocalDateTime endAt,
                             Integer capacity, Boolean waitlistEnabled, Boolean manualReviewRequired,
                             String registrationFormSchema, String status,
                             Long actorId, String actorRole) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));

        // Object-level authorization: only the event creator can update it
        if (!event.getCreatedBy().equals(actorId)) {
            throw new SecurityException("Cannot update an event you did not create");
        }

        // Validate org scope for the event's organization
        validateOrganizationScope(actorId, event.getOrganizationId(), actorRole);

        String beforeStatus = event.getStatus();

        if (title != null) event.setTitle(title);
        if (description != null) event.setDescription(description);
        if (mode != null) event.setMode(mode);
        if (location != null) event.setLocation(location);
        if (startAt != null) event.setStartAt(startAt);
        if (endAt != null) event.setEndAt(endAt);
        if (capacity != null) event.setCapacity(capacity);
        if (waitlistEnabled != null) event.setWaitlistEnabled(waitlistEnabled);
        if (manualReviewRequired != null) event.setManualReviewRequired(manualReviewRequired);
        if (registrationFormSchema != null) event.setRegistrationFormSchema(registrationFormSchema);
        if (status != null) event.setStatus(status);
        event.setUpdatedAt(LocalDateTime.now());

        Event saved = eventRepository.save(event);

        auditService.log(actorId, actorRole, "EVENT_UPDATED",
                "Event", saved.getId().toString(),
                beforeStatus, saved.getStatus(), null, null);

        return saved;
    }

    @Transactional
    public EventRegistration register(Long eventId, Long accountId, String formResponses) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        // Validate formResponses against registration form schema if defined
        validateFormResponses(event, formResponses);

        long approvedCount = registrationRepository.countByEventIdAndStatus(eventId, "APPROVED");

        String regStatus;
        if (event.isManualReviewRequired()) {
            regStatus = "PENDING_REVIEW";
        } else if (approvedCount < event.getCapacity()) {
            regStatus = "APPROVED";
        } else if (event.isWaitlistEnabled()) {
            regStatus = "WAITLISTED";
        } else {
            throw new IllegalStateException("Event is full and waitlist is not enabled");
        }

        EventRegistration reg = new EventRegistration();
        reg.setEventId(eventId);
        reg.setAccountId(accountId);
        reg.setStatus(regStatus);
        reg.setFormResponses(formResponses);
        reg.setCreatedAt(LocalDateTime.now());
        reg.setUpdatedAt(LocalDateTime.now());

        EventRegistration saved = registrationRepository.save(reg);

        auditService.log(accountId, null, "REGISTRATION_SUBMITTED",
                "EventRegistration", saved.getId().toString(),
                null, regStatus, null, null);

        return saved;
    }

    @Transactional
    public EventRegistration decideRegistration(Long regId, String decision, String note,
                                                 Long actorId, String actorRole) {
        if (!"APPROVE".equals(decision) && !"DENY".equals(decision)) {
            throw new IllegalArgumentException("Decision must be APPROVE or DENY");
        }

        EventRegistration reg = registrationRepository.findById(regId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found: " + regId));

        // Verify org scope: the registration's event must belong to actor's org
        Event regEvent = eventRepository.findById(reg.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + reg.getEventId()));
        validateOrganizationScope(actorId, regEvent.getOrganizationId(), actorRole);

        String beforeStatus = reg.getStatus();

        if ("APPROVE".equals(decision)) {
            Event event = eventRepository.findById(reg.getEventId())
                    .orElseThrow(() -> new IllegalArgumentException("Event not found: " + reg.getEventId()));
            long approvedCount = registrationRepository.countByEventIdAndStatus(reg.getEventId(), "APPROVED");
            if (approvedCount >= event.getCapacity()) {
                throw new IllegalStateException("Event is at capacity, cannot approve registration");
            }
            reg.setStatus("APPROVED");
        } else {
            reg.setStatus("DENIED");
        }

        reg.setReviewNote(note);
        reg.setReviewedBy(actorId);
        reg.setUpdatedAt(LocalDateTime.now());

        EventRegistration saved = registrationRepository.save(reg);

        auditService.log(actorId, actorRole, "REGISTRATION_DECIDED",
                "EventRegistration", saved.getId().toString(),
                beforeStatus, saved.getStatus(), decision, null);

        return saved;
    }

    @Transactional
    public EventRegistration cancelRegistration(Long regId, Long actorId) {
        EventRegistration reg = registrationRepository.findById(regId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found: " + regId));

        if (!reg.getAccountId().equals(actorId)) {
            throw new SecurityException("Cannot cancel another user's registration");
        }

        String beforeStatus = reg.getStatus();
        boolean wasApproved = "APPROVED".equals(beforeStatus);

        reg.setStatus("CANCELLED");
        reg.setUpdatedAt(LocalDateTime.now());
        registrationRepository.save(reg);

        auditService.log(actorId, null, "REGISTRATION_CANCELLED",
                "EventRegistration", reg.getId().toString(),
                beforeStatus, "CANCELLED", null, null);

        // Auto-promote oldest waitlisted entry if the cancelled reg was approved
        if (wasApproved) {
            List<EventRegistration> waitlisted = registrationRepository
                    .findByEventIdAndStatus(reg.getEventId(), "WAITLISTED");
            if (!waitlisted.isEmpty()) {
                // Find oldest by createdAt
                EventRegistration oldest = waitlisted.stream()
                        .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                        .get();
                oldest.setStatus("APPROVED");
                oldest.setUpdatedAt(LocalDateTime.now());
                registrationRepository.save(oldest);

                auditService.log(actorId, null, "REGISTRATION_AUTO_PROMOTED",
                        "EventRegistration", oldest.getId().toString(),
                        "WAITLISTED", "APPROVED", "WAITLIST_PROMOTION", null);
            }
        }

        return reg;
    }

    public List<EventRegistration> getPendingRegistrations(Long actorId, String actorRole) {
        if ("ADMIN".equals(actorRole)) {
            return registrationRepository.findByStatus("PENDING_REVIEW");
        }
        List<String> orgIds = getActorOrgScopes(actorId);
        if (orgIds.isEmpty()) {
            return List.of();
        }
        return registrationRepository.findByStatusAndEventOrganizationIdIn("PENDING_REVIEW", orgIds);
    }

    private List<String> getActorOrgScopes(Long actorId) {
        return roleMembershipRepository.findByAccountIdAndStatus(
                        actorId, RoleMembership.RoleMembershipStatus.APPROVED).stream()
                .filter(m -> "ORG_OPERATOR".equals(m.getRoleType()))
                .map(RoleMembership::getScopeId)
                .filter(s -> s != null)
                .toList();
    }

    public List<EventRegistration> getRoster(Long eventId) {
        return registrationRepository.findByEventIdAndStatus(eventId, "APPROVED");
    }

    /**
     * Validates submitted form responses against the event's registration form schema.
     * Schema format: JSON array of field definitions, each with id, type, label, required.
     * Responses format: JSON object mapping field id to value.
     */
    private void validateFormResponses(Event event, String formResponses) {
        String schema = event.getRegistrationFormSchema();
        if (schema == null || schema.isBlank()) {
            return; // No custom schema — nothing to validate
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode schemaNode = mapper.readTree(schema);
            com.fasterxml.jackson.databind.JsonNode responsesNode =
                    (formResponses != null && !formResponses.isBlank())
                            ? mapper.readTree(formResponses)
                            : mapper.createObjectNode();

            if (!schemaNode.isArray()) return;

            for (com.fasterxml.jackson.databind.JsonNode field : schemaNode) {
                String fieldId = field.has("id") ? field.get("id").asText() : null;
                boolean required = field.has("required") && field.get("required").asBoolean();
                if (required && fieldId != null) {
                    if (!responsesNode.has(fieldId)
                            || responsesNode.get(fieldId).isNull()
                            || responsesNode.get(fieldId).asText().isBlank()) {
                        String label = field.has("label") ? field.get("label").asText() : fieldId;
                        throw new IllegalArgumentException("Required field missing: " + label);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            // Malformed JSON in responses — reject
            throw new IllegalArgumentException("Invalid form responses format");
        }
    }

    private void validateOrganizationScope(Long actorId, String organizationId, String actorRole) {
        // ADMINs bypass org scope checks
        if ("ADMIN".equals(actorRole)) {
            return;
        }
        boolean hasScope = roleMembershipRepository.existsByAccountIdAndRoleTypeAndScopeIdAndStatus(
                actorId, "ORG_OPERATOR", organizationId, RoleMembership.RoleMembershipStatus.APPROVED);
        if (!hasScope) {
            throw new SecurityException(
                    "Not authorized for organization: " + organizationId);
        }
    }

    /**
     * Exports event roster as a local file artifact (CSV or PDF).
     * Returns the relative file path for retrieval.
     */
    public String exportRoster(Long eventId, String format, Long actorId, String actorRole) {
        List<EventRegistration> approved = registrationRepository.findByEventIdAndStatus(eventId, "APPROVED");
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        List<String> headers = List.of("Registration ID", "Event ID", "Account ID", "Status", "Registered At");
        List<List<String>> rows = new ArrayList<>();
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append("\n");

        for (EventRegistration reg : approved) {
            List<String> row = List.of(
                    reg.getId().toString(),
                    reg.getEventId().toString(),
                    reg.getAccountId().toString(),
                    reg.getStatus(),
                    reg.getCreatedAt().toString());
            rows.add(row);
            csv.append(String.join(",", row)).append("\n");
        }

        String exportPath;
        if ("PDF".equalsIgnoreCase(format)) {
            exportPath = exportService.exportPdf("Roster: " + event.getTitle(), headers, rows, "roster-" + eventId);
        } else {
            exportPath = exportService.exportCsv(csv.toString(), "roster-" + eventId);
        }

        auditService.log(actorId, actorRole, "ROSTER_EXPORTED",
                "Event", eventId.toString(),
                null, exportPath, format, null);

        return exportPath;
    }
}
