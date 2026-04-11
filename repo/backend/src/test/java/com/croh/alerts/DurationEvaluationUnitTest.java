package com.croh.alerts;

import com.croh.account.AccountRepository;
import com.croh.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests of {@link AlertService#evaluateSustainedDuration} proving the
 * streak-walk algorithm with precise timestamps. Each test:
 *   1. Seeds backdated events into the repository.
 *   2. Calls evaluateSustainedDuration with an explicit current reading and `now`.
 *   3. Asserts on the structured {@link AlertService.DurationEvaluation} fields.
 *
 * <p>Visual timeline diagrams document each scenario for static review.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class DurationEvaluationUnitTest {

    @Autowired private AlertService alertService;
    @Autowired private AlertEventRepository alertEventRepository;
    @Autowired private AlertRuleDefaultRepository ruleDefaultRepository;
    @Autowired private AlertRuleOverrideRepository ruleOverrideRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private static final double THRESHOLD = 100.0;
    private static final String OP = "GT";
    private static final int REQUIRED = 300; // seconds

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        workOrderRepository.deleteAll();
        alertEventRepository.deleteAll();
        ruleOverrideRepository.deleteAll();
        ruleDefaultRepository.deleteAll();
        accountRepository.deleteAll();
        now = LocalDateTime.now();
    }

    // ── Case 1: No prior events, current reading exceeds ──
    // Timeline:  (empty history)  ──  [110°F @ now]
    //            Streak starts at now → age 0s < 300s required
    //            → NOT satisfied
    @Test
    void noPriorEvents_currentExceeds_streakStartsAtNow_notSatisfied() {
        var result = evaluate("C1", "s1", 110); // > 100

        assertFalse(result.satisfied());
        assertNotNull(result.streakStartedAt(), "Streak should start at now from current reading");
        assertEquals(0, result.streakSeconds());
        assertEquals(REQUIRED, result.requiredSeconds());
    }

    // ── Case 2: No prior events, current reading does NOT exceed ──
    // Timeline:  (empty history)  ──  [90°F @ now]
    //            → NOT satisfied, no active streak
    @Test
    void noPriorEvents_currentBelowThreshold_noStreak() {
        var result = evaluate("C2", "s2", 90); // <= 100

        assertFalse(result.satisfied());
        assertNull(result.streakStartedAt());
        assertEquals(0, result.streakSeconds());
    }

    // ── Case 3: Old exceeding event + current exceeds ──
    // Timeline:  [110°F @ -400s] ──────────── [120°F @ now]
    //            |<───── unbroken streak: 400s ─────>|
    //            required: 300s → satisfied
    @Test
    void oldExceedance_currentExceeds_sustainedStreak_satisfied() {
        seedEvent("C3", "s3", 110, 400);

        var result = evaluate("C3", "s3", 120);

        assertTrue(result.satisfied());
        assertNotNull(result.streakStartedAt());
        assertTrue(result.streakSeconds() >= 300,
                "Streak must span >= 300s, actual: " + result.streakSeconds());
        assertEquals(REQUIRED, result.requiredSeconds());
    }

    // ── Case 4: Old exceeding event + current does NOT exceed ──
    // Timeline:  [110°F @ -400s] ──────────── [90°F @ now]
    //            |<── old streak ──>|           BREAK
    //            → NOT satisfied (current reading breaks streak)
    @Test
    void oldExceedance_currentBelowThreshold_streakBroken_notSatisfied() {
        seedEvent("C4", "s4", 110, 400);

        var result = evaluate("C4", "s4", 90);

        assertFalse(result.satisfied());
        assertNull(result.streakStartedAt());
        assertEquals(0, result.streakSeconds());
    }

    // ── Case 5: Streak broken by normal reading mid-window ──
    // Timeline:  [115°F @ -400s]  [90°F @ -100s]  [120°F @ -50s]  [130°F @ now]
    //            |<── old ──>|     BREAK           |<── new: ~50s ──────>|
    //            required: 300s → NOT satisfied (new streak only ~50s)
    @Test
    void streakBrokenByNormalReading_newStreakTooShort_notSatisfied() {
        seedEvent("C5", "s5", 115, 400);
        seedEvent("C5", "s5", 90, 100);   // breaks streak
        seedEvent("C5", "s5", 120, 50);    // new streak starts

        var result = evaluate("C5", "s5", 130);

        assertFalse(result.satisfied());
        assertNotNull(result.streakStartedAt());
        assertTrue(result.streakSeconds() < 300,
                "New streak after break must be < 300s, actual: " + result.streakSeconds());
    }

    // ── Case 6: Multiple exceeding events spanning full duration ──
    // Timeline:  [110°F @ -500s]  [115°F @ -400s]  [120°F @ -200s]  [125°F @ now]
    //            |<───────────── unbroken streak: ~500s ──────────────────>|
    //            required: 300s → satisfied
    @Test
    void multipleExceedances_unbrokenStreak_spansDuration_satisfied() {
        seedEvent("C6", "s6", 110, 500);
        seedEvent("C6", "s6", 115, 400);
        seedEvent("C6", "s6", 120, 200);

        var result = evaluate("C6", "s6", 125);

        assertTrue(result.satisfied());
        assertTrue(result.streakSeconds() >= 300);
    }

    // ── Case 7: Streak comfortably past boundary ──
    // Timeline:  [110°F @ -301s]  [115°F @ now]
    //            |<── streak: ~301s ──>|
    //            required: 300s → satisfied (>= comparison)
    @Test
    void streakPastBoundary_satisfied() {
        seedEvent("C7", "s7", 110, 301);

        var result = evaluate("C7", "s7", 115);

        assertTrue(result.satisfied());
        assertTrue(result.streakSeconds() >= 300);
    }

    // ── Case 8: Recent exceeding event, too short ──
    // Timeline:  [110°F @ -100s]  [115°F @ now]
    //            |<── streak: ~100s ──>|
    //            required: 300s → NOT satisfied
    @Test
    void recentExceedance_streakTooShort_notSatisfied() {
        seedEvent("C8", "s8", 110, 100);

        var result = evaluate("C8", "s8", 115);

        assertFalse(result.satisfied());
        assertNotNull(result.streakStartedAt());
        assertTrue(result.streakSeconds() < 300);
    }

    // ── Helpers ──

    private AlertService.DurationEvaluation evaluate(String alertType, String scopeId,
                                                      double currentValue) {
        return alertService.evaluateSustainedDuration(
                alertType, "STATION", scopeId,
                REQUIRED, THRESHOLD, OP,
                currentValue, now);
    }

    private void seedEvent(String alertType, String scopeId,
                            double measuredValue, int secondsAgo) {
        AlertEvent e = new AlertEvent();
        e.setAlertType(alertType);
        e.setScopeType("STATION");
        e.setScopeId(scopeId);
        e.setSeverity("HIGH");
        e.setMeasuredValue(measuredValue);
        e.setMeasuredUnit("F");
        e.setSuppressed(false);
        e.setCreatedAt(now.minusSeconds(secondsAgo));
        alertEventRepository.save(e);
    }
}
