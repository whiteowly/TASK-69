package com.croh.alerts;

import com.croh.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AlertService {

    private final AlertRuleDefaultRepository defaultRepository;
    private final AlertRuleOverrideRepository overrideRepository;
    private final AlertEventRepository alertEventRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AuditService auditService;

    public AlertService(AlertRuleDefaultRepository defaultRepository,
                        AlertRuleOverrideRepository overrideRepository,
                        AlertEventRepository alertEventRepository,
                        WorkOrderRepository workOrderRepository,
                        AuditService auditService) {
        this.defaultRepository = defaultRepository;
        this.overrideRepository = overrideRepository;
        this.alertEventRepository = alertEventRepository;
        this.workOrderRepository = workOrderRepository;
        this.auditService = auditService;
    }

    public Map<String, List<?>> getRules() {
        Map<String, List<?>> result = new HashMap<>();
        result.put("defaults", defaultRepository.findAll());
        result.put("overrides", overrideRepository.findAll());
        return result;
    }

    @Transactional
    public AlertRuleDefault updateDefault(String alertType, String severity,
                                           String thresholdOperator, double thresholdValue,
                                           String thresholdUnit, int durationSeconds,
                                           int cooldownSeconds, Long actorId, String actorRole) {
        AlertRuleDefault rule = defaultRepository.findByAlertType(alertType)
                .orElseGet(() -> {
                    AlertRuleDefault newRule = new AlertRuleDefault();
                    newRule.setAlertType(alertType);
                    return newRule;
                });

        rule.setSeverity(severity);
        rule.setThresholdOperator(thresholdOperator);
        rule.setThresholdValue(thresholdValue);
        rule.setThresholdUnit(thresholdUnit);
        rule.setDurationSeconds(durationSeconds);
        rule.setCooldownSeconds(cooldownSeconds);
        rule.setUpdatedAt(LocalDateTime.now());

        AlertRuleDefault saved = defaultRepository.save(rule);

        auditService.log(actorId, actorRole, "ALERT_RULE_DEFAULT_UPDATED",
                "AlertRuleDefault", saved.getId().toString(),
                null, null, alertType, null);

        return saved;
    }

    @Transactional
    public AlertRuleOverride updateOverride(String alertType, String scopeType, String scopeId,
                                             String severity, String thresholdOperator,
                                             double thresholdValue, String thresholdUnit,
                                             int durationSeconds, int cooldownSeconds,
                                             Long actorId, String actorRole) {
        AlertRuleOverride rule = overrideRepository
                .findByAlertTypeAndScopeTypeAndScopeId(alertType, scopeType, scopeId)
                .orElseGet(() -> {
                    AlertRuleOverride newRule = new AlertRuleOverride();
                    newRule.setAlertType(alertType);
                    newRule.setScopeType(scopeType);
                    newRule.setScopeId(scopeId);
                    return newRule;
                });

        rule.setSeverity(severity);
        rule.setThresholdOperator(thresholdOperator);
        rule.setThresholdValue(thresholdValue);
        rule.setThresholdUnit(thresholdUnit);
        rule.setDurationSeconds(durationSeconds);
        rule.setCooldownSeconds(cooldownSeconds);
        rule.setUpdatedAt(LocalDateTime.now());

        AlertRuleOverride saved = overrideRepository.save(rule);

        auditService.log(actorId, actorRole, "ALERT_RULE_OVERRIDE_UPDATED",
                "AlertRuleOverride", saved.getId().toString(),
                null, null, alertType, null);

        return saved;
    }

    @Transactional
    public AlertEventResult ingestEvent(String alertType, String scopeType, String scopeId,
                                         double measuredValue, String unit) {
        // Single timestamp for the entire evaluation — keeps duration calc, event
        // persistence, and WO creation temporally consistent.
        final LocalDateTime now = LocalDateTime.now();

        // Find applicable rule: override first, then default
        Optional<AlertRuleOverride> overrideOpt = overrideRepository
                .findByAlertTypeAndScopeTypeAndScopeId(alertType, scopeType, scopeId);
        Optional<AlertRuleDefault> defaultOpt = defaultRepository.findByAlertType(alertType);

        String severity = "MEDIUM";
        double threshold = 0;
        String operator = "GT";
        int cooldownSeconds = 900;
        int durationSeconds = 0;

        if (overrideOpt.isPresent()) {
            AlertRuleOverride override = overrideOpt.get();
            severity = override.getSeverity();
            threshold = override.getThresholdValue();
            operator = override.getThresholdOperator();
            cooldownSeconds = override.getCooldownSeconds();
            durationSeconds = override.getDurationSeconds();
        } else if (defaultOpt.isPresent()) {
            AlertRuleDefault def = defaultOpt.get();
            severity = def.getSeverity();
            threshold = def.getThresholdValue();
            operator = def.getThresholdOperator();
            cooldownSeconds = def.getCooldownSeconds();
            durationSeconds = def.getDurationSeconds();
        }

        // ── Duration-window evaluation (sustained-threshold semantics) ──
        //
        // When durationSeconds > 0 the threshold must have been CONTINUOUSLY
        // exceeded for at least that many seconds before a work order is created.
        // Example: "temperature > 120 °F for 5 continuous minutes."
        //
        // The evaluation includes the *current* reading as the newest sample in
        // the streak walk, so the trigger decision reflects the full timeline up
        // to and including this event.  A single `now` timestamp is used for
        // streak-age calculation, event persistence, and WO creation.
        //
        // If durationSeconds == 0, duration is trivially satisfied (immediate).
        DurationEvaluation durationEval;
        if (durationSeconds == 0) {
            durationEval = DurationEvaluation.immediate();
        } else {
            durationEval = evaluateSustainedDuration(
                    alertType, scopeType, scopeId,
                    durationSeconds, threshold, operator,
                    measuredValue, now);
        }

        // Check cooldown
        LocalDateTime cooldownCutoff = now.minusSeconds(cooldownSeconds);
        Optional<AlertEvent> recentEvent = alertEventRepository.findLatestNonSuppressed(
                alertType, scopeType, scopeId, cooldownCutoff);

        boolean suppressed = recentEvent.isPresent();

        // Persist the incoming event
        AlertEvent event = new AlertEvent();
        event.setAlertType(alertType);
        event.setScopeType(scopeType);
        event.setScopeId(scopeId);
        event.setSeverity(severity);
        event.setMeasuredValue(measuredValue);
        event.setMeasuredUnit(unit);
        event.setSuppressed(suppressed);
        event.setCreatedAt(now);

        AlertEvent savedEvent = alertEventRepository.save(event);

        WorkOrder workOrder = null;

        // Auto-create work order when all three conditions hold:
        //   1. threshold exceeded by current reading
        //   2. not suppressed by cooldown
        //   3. sustained-duration requirement met (includes current reading)
        if (!suppressed && thresholdExceeded(measuredValue, threshold, operator) && durationEval.satisfied()) {
            workOrder = new WorkOrder();
            workOrder.setAlertEventId(savedEvent.getId());
            workOrder.setTitle("Alert: " + alertType + " [" + scopeType + ":" + scopeId + "]");
            workOrder.setDescription("Measured value " + measuredValue + " " + unit +
                    " exceeded threshold " + threshold);
            workOrder.setSeverity(severity);
            workOrder.setStatus("NEW_ALERT");
            workOrder.setCreatedBy(0L); // system
            workOrder.setCreatedAt(now);
            workOrder.setUpdatedAt(now);
            workOrder = workOrderRepository.save(workOrder);

            auditService.log(0L, "SYSTEM", "WORK_ORDER_AUTO_CREATED",
                    "WorkOrder", workOrder.getId().toString(),
                    null, "NEW_ALERT", alertType, null);
        }

        auditService.log(0L, "SYSTEM", "ALERT_EVENT_INGESTED",
                "AlertEvent", savedEvent.getId().toString(),
                null, suppressed ? "SUPPRESSED" : "ACTIVE", alertType, null);

        return new AlertEventResult(savedEvent, workOrder, durationEval);
    }

    /**
     * Evaluates whether the threshold-exceeding condition has been sustained (unbroken)
     * for at least {@code durationSeconds}, <strong>including the current reading</strong>
     * as the newest sample in the timeline.
     *
     * <h3>Sustained-threshold duration model</h3>
     * <p>Implements the prompt's "configurable threshold duration window" as a
     * <strong>sustained-exceedance streak</strong>: the threshold must have been
     * continuously exceeded — with no intervening normal reading — for at least
     * {@code durationSeconds} before a work order is auto-created.</p>
     *
     * <h3>Algorithm (streak-walk including current reading)</h3>
     * <ol>
     *   <li>Query all persisted events for this alert type + scope within a lookback
     *       of {@code max(2 * durationSeconds, 3600)} seconds, oldest-first.</li>
     *   <li>Walk them chronologically.  For each event:
     *       <ul>
     *         <li>Exceeds threshold → start or continue streak.</li>
     *         <li>Does not exceed → <strong>reset</strong> streak (condition cleared).</li>
     *       </ul></li>
     *   <li><strong>Apply the current (not-yet-persisted) reading as the final sample.</strong>
     *       If it exceeds the threshold, the streak either continues or starts at
     *       {@code now}; if not, the streak resets.</li>
     *   <li>Compute {@code streakSeconds = Duration(streakStart, now)}.</li>
     *   <li>Return {@code satisfied = (streakSeconds >= durationSeconds)}.</li>
     * </ol>
     *
     * <p>Including the current reading eliminates an off-by-one-event gap: without it
     * the first exceedance after a cold start would see zero history and immediately
     * return "not satisfied", even though the current reading itself exceeds the
     * threshold and should begin the streak.</p>
     *
     * @param measuredValue the current sensor reading (not yet persisted)
     * @param now           single reference timestamp shared with event persistence
     * @return {@link DurationEvaluation} with verdict, streak start, elapsed/required seconds
     */
    DurationEvaluation evaluateSustainedDuration(String alertType, String scopeType, String scopeId,
                                                  int durationSeconds, double threshold, String operator,
                                                  double measuredValue, LocalDateTime now) {
        // Look back 2× duration (min 1 hour) to capture streak start with margin
        long lookbackSecs = Math.max((long) durationSeconds * 2, 3600);
        LocalDateTime lookback = now.minusSeconds(lookbackSecs);
        List<AlertEvent> history = alertEventRepository
                .findByAlertTypeAndScopeTypeAndScopeIdAndCreatedAtAfterOrderByCreatedAtAsc(
                        alertType, scopeType, scopeId, lookback);

        // ── Walk persisted events chronologically ──
        LocalDateTime streakStart = null;
        for (AlertEvent e : history) {
            if (thresholdExceeded(e.getMeasuredValue(), threshold, operator)) {
                if (streakStart == null) {
                    streakStart = e.getCreatedAt();
                }
            } else {
                streakStart = null;  // condition cleared — streak resets
            }
        }

        // ── Apply the current (not-yet-persisted) reading as the newest sample ──
        if (thresholdExceeded(measuredValue, threshold, operator)) {
            if (streakStart == null) {
                streakStart = now;   // streak begins with this reading
            }
        } else {
            streakStart = null;      // current reading is normal — no active streak
        }

        if (streakStart == null) {
            return new DurationEvaluation(false, null, 0, durationSeconds);
        }

        long streakSeconds = java.time.Duration.between(streakStart, now).getSeconds();
        boolean satisfied = streakSeconds >= durationSeconds;
        return new DurationEvaluation(satisfied, streakStart, streakSeconds, durationSeconds);
    }

    private boolean thresholdExceeded(double measured, double threshold, String operator) {
        return switch (operator) {
            case "GT" -> measured > threshold;
            case "GTE" -> measured >= threshold;
            case "LT" -> measured < threshold;
            case "LTE" -> measured <= threshold;
            case "EQ" -> measured == threshold;
            default -> measured > threshold;
        };
    }

    /**
     * Result of duration-window evaluation. Exposes the evaluation inputs and outputs
     * so callers (and API responses) can trace exactly why the duration check passed or failed.
     *
     * @param satisfied      true if the sustained-exceedance streak meets the required duration
     * @param streakStartedAt  timestamp of the first event in the current unbroken streak, or null
     * @param streakSeconds  elapsed seconds from streak start to evaluation time, or 0
     * @param requiredSeconds the configured durationSeconds for this rule
     */
    public record DurationEvaluation(boolean satisfied, LocalDateTime streakStartedAt,
                                      long streakSeconds, int requiredSeconds) {
        /** Duration is trivially satisfied when the rule requires 0 seconds. */
        static DurationEvaluation immediate() {
            return new DurationEvaluation(true, null, 0, 0);
        }
    }

    public record AlertEventResult(AlertEvent alertEvent, WorkOrder workOrder,
                                    DurationEvaluation durationEvaluation) {}
}
