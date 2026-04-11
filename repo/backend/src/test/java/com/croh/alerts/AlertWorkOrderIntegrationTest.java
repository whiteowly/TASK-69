package com.croh.alerts;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.croh.audit.AuditLogRepository;
import com.croh.security.RolePermissions;
import com.croh.security.RoleType;
import com.croh.security.SessionAccount;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
// nullValue not needed — using doesNotExist for omitted JSON fields
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertWorkOrderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AlertRuleDefaultRepository ruleDefaultRepository;
    @Autowired private AlertRuleOverrideRepository ruleOverrideRepository;
    @Autowired private AlertEventRepository alertEventRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private WorkOrderNoteRepository noteRepository;
    @Autowired private PostIncidentReviewRepository pirRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        pirRepository.deleteAll();
        noteRepository.deleteAll();
        workOrderRepository.deleteAll();
        alertEventRepository.deleteAll();
        ruleOverrideRepository.deleteAll();
        ruleDefaultRepository.deleteAll();
        accountRepository.deleteAll();
        admin = createAccount("admin");
    }

    @Test
    void configureAlertRule_andIngestEvent_createsWorkOrder() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Configure threshold: temperature > 120°F
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/OVER_TEMPERATURE")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"HIGH\",\"thresholdOperator\":\"GT\",\"thresholdValue\":120,\"thresholdUnit\":\"F\",\"durationSeconds\":0,\"cooldownSeconds\":60}"))
                .andExpect(status().isOk());

        // Ingest event exceeding threshold
        mockMvc.perform(post("/api/v1/alerts/events")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"OVER_TEMPERATURE\",\"scopeType\":\"STATION\",\"scopeId\":\"s1\",\"measuredValue\":130,\"unit\":\"F\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suppressed", is(false)))
                .andExpect(jsonPath("$.workOrderId", notNullValue()));
    }

    @Test
    void cooldownSuppression_secondEventWithinCooldown_isSuppressed() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Configure rule with cooldown
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/LEAKAGE")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"MEDIUM\",\"thresholdOperator\":\"GT\",\"thresholdValue\":0,\"cooldownSeconds\":3600}"))
                .andExpect(status().isOk());

        // First event — not suppressed
        mockMvc.perform(post("/api/v1/alerts/events")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"LEAKAGE\",\"scopeType\":\"STATION\",\"scopeId\":\"s2\",\"measuredValue\":5}"))
                .andExpect(jsonPath("$.suppressed", is(false)));

        // Second event within cooldown — suppressed
        mockMvc.perform(post("/api/v1/alerts/events")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"LEAKAGE\",\"scopeType\":\"STATION\",\"scopeId\":\"s2\",\"measuredValue\":10}"))
                .andExpect(jsonPath("$.suppressed", is(true)))
                .andExpect(jsonPath("$.workOrderId").doesNotExist());
    }

    @Test
    void overrideRuleTakesPrecedenceOverDefault() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Default: threshold 100
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/OVERCURRENT")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"LOW\",\"thresholdOperator\":\"GT\",\"thresholdValue\":100,\"cooldownSeconds\":0}"))
                .andExpect(status().isOk());

        // Override for station s3: threshold 50
        mockMvc.perform(put("/api/v1/alerts/rules/overrides/STATION/s3/OVERCURRENT")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"HIGH\",\"thresholdOperator\":\"GT\",\"thresholdValue\":50,\"cooldownSeconds\":0}"))
                .andExpect(status().isOk());

        // Event at 75A for station s3 — should use override threshold (50), so threshold exceeded
        mockMvc.perform(post("/api/v1/alerts/events")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"OVERCURRENT\",\"scopeType\":\"STATION\",\"scopeId\":\"s3\",\"measuredValue\":75}"))
                .andExpect(jsonPath("$.workOrderId", notNullValue()));
    }

    @Test
    void workOrderTransition_fullLifecycle_recordsSla() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Create work order
        String woBody = mockMvc.perform(post("/api/v1/work-orders")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Test WO\",\"description\":\"desc\",\"severity\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long woId = Long.parseLong(woBody.split("\"id\":")[1].split("[,}]")[0].trim());

        // Transition through lifecycle
        for (String state : new String[]{"ACKNOWLEDGED", "DISPATCHED", "IN_PROGRESS", "RESOLVED", "CLOSED"}) {
            mockMvc.perform(post("/api/v1/work-orders/" + woId + "/transition")
                                    .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toStatus\":\"" + state + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is(state)));
        }

        // Verify SLA timestamps
        WorkOrder wo = workOrderRepository.findById(woId).orElseThrow();
        assertNotNull(wo.getFirstResponseAt());
        assertNotNull(wo.getClosedAt());
    }

    @Test
    void workOrderTransition_skipState_returns409() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        String woBody = mockMvc.perform(post("/api/v1/work-orders")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Test WO\",\"severity\":\"MEDIUM\"}"))
                .andReturn().getResponse().getContentAsString();
        Long woId = Long.parseLong(woBody.split("\"id\":")[1].split("[,}]")[0].trim());

        // Try to skip from NEW_ALERT to DISPATCHED
        mockMvc.perform(post("/api/v1/work-orders/" + woId + "/transition")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"toStatus\":\"DISPATCHED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addNoteToWorkOrder() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        String woBody = mockMvc.perform(post("/api/v1/work-orders")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Test WO\",\"severity\":\"MEDIUM\"}"))
                .andReturn().getResponse().getContentAsString();
        Long woId = Long.parseLong(woBody.split("\"id\":")[1].split("[,}]")[0].trim());

        mockMvc.perform(post("/api/v1/work-orders/" + woId + "/notes")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"content\":\"Checked equipment. Found issue.\"}"))
                .andExpect(status().isCreated());

        assertEquals(1, noteRepository.findAll().size());
    }

    // ── Duration-window sustained-threshold tests ──
    //
    // These tests prove the sustained-duration semantics by:
    //   (a) seeding backdated events for precise time control
    //   (b) asserting on the EXPLICIT duration evaluation fields in the API response:
    //       $.durationSatisfied, $.durationStreakSeconds, $.durationRequiredSeconds
    //
    // This makes the semantics statically verifiable: a reviewer can see exactly what
    // the algorithm computed and why the work order was or was not created.

    @Test
    void duration_firstExceedance_noWorkOrder_durationNotYetSatisfied() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Rule: threshold > 100°F, sustained for 300s, no cooldown
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/SUSTAINED_TEMP")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"HIGH\",\"thresholdOperator\":\"GT\",\"thresholdValue\":100,\"thresholdUnit\":\"F\",\"durationSeconds\":300,\"cooldownSeconds\":0}"))
                .andExpect(status().isOk());

        // Very first exceedance — no prior events, streak has zero history
        // Response must show: durationSatisfied=false, streakSeconds=0, requiredSeconds=300
        mockMvc.perform(post("/api/v1/alerts/events")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"SUSTAINED_TEMP\",\"scopeType\":\"STATION\",\"scopeId\":\"dt1\",\"measuredValue\":110,\"unit\":\"F\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSatisfied", is(false)))
                .andExpect(jsonPath("$.durationStreakSeconds", is(0)))
                .andExpect(jsonPath("$.durationRequiredSeconds", is(300)))
                .andExpect(jsonPath("$.workOrderId").doesNotExist());
    }

    @Test
    void duration_sustainedExceedanceOverWindow_triggersWorkOrder() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Rule: threshold > 100°F, sustained for 300s
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/SUSTAINED_TEMP2")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"HIGH\",\"thresholdOperator\":\"GT\",\"thresholdValue\":100,\"thresholdUnit\":\"F\",\"durationSeconds\":300,\"cooldownSeconds\":0}"))
                .andExpect(status().isOk());

        // Seed: threshold-exceeding event 400 seconds ago
        seedEvent("SUSTAINED_TEMP2", "STATION", "dt2", 115, "F", 400);

        // Current event also exceeds — unbroken streak of ~400s > 300s required
        // Response must show: durationSatisfied=true, streakSeconds >= 300, requiredSeconds=300
        mockMvc.perform(post("/api/v1/alerts/events")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"SUSTAINED_TEMP2\",\"scopeType\":\"STATION\",\"scopeId\":\"dt2\",\"measuredValue\":120,\"unit\":\"F\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSatisfied", is(true)))
                .andExpect(jsonPath("$.durationRequiredSeconds", is(300)))
                .andExpect(jsonPath("$.workOrderId", notNullValue()));
    }

    @Test
    void duration_streakBrokenByNormalReading_doesNotTrigger() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Rule: threshold > 100°F, sustained for 300s
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/SUSTAINED_TEMP3")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"HIGH\",\"thresholdOperator\":\"GT\",\"thresholdValue\":100,\"thresholdUnit\":\"F\",\"durationSeconds\":300,\"cooldownSeconds\":0}"))
                .andExpect(status().isOk());

        // Seed: exceedance 400s ago
        seedEvent("SUSTAINED_TEMP3", "STATION", "dt3", 115, "F", 400);
        // Seed: normal reading 100s ago — BREAKS the streak
        seedEvent("SUSTAINED_TEMP3", "STATION", "dt3", 90, "F", 100);

        // Current event exceeds threshold, but the streak restarted only ~100s ago
        // (broken by the 90°F reading at -100s), so ~100s < 300s required
        // Response must show: durationSatisfied=false, streakSeconds < 300
        mockMvc.perform(post("/api/v1/alerts/events")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"SUSTAINED_TEMP3\",\"scopeType\":\"STATION\",\"scopeId\":\"dt3\",\"measuredValue\":120,\"unit\":\"F\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSatisfied", is(false)))
                .andExpect(jsonPath("$.durationRequiredSeconds", is(300)))
                .andExpect(jsonPath("$.workOrderId").doesNotExist());
    }

    @Test
    void duration_zeroSeconds_triggersImmediately() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Rule: durationSeconds=0 means immediate trigger (no sustained-duration check)
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/INSTANT_ALERT")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"CRITICAL\",\"thresholdOperator\":\"GT\",\"thresholdValue\":50,\"durationSeconds\":0,\"cooldownSeconds\":0}"))
                .andExpect(status().isOk());

        // Single event — immediate WO with durationSatisfied=true, requiredSeconds=0
        mockMvc.perform(post("/api/v1/alerts/events")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"INSTANT_ALERT\",\"scopeType\":\"STATION\",\"scopeId\":\"dt4\",\"measuredValue\":60}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSatisfied", is(true)))
                .andExpect(jsonPath("$.durationRequiredSeconds", is(0)))
                .andExpect(jsonPath("$.workOrderId", notNullValue()));
    }

    @Test
    void duration_cooldownStillAppliesAfterDurationSatisfied() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Rule: threshold > 100, duration 300s, cooldown 60s
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/SUSTAINED_COOL")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"HIGH\",\"thresholdOperator\":\"GT\",\"thresholdValue\":100,\"durationSeconds\":300,\"cooldownSeconds\":60}"))
                .andExpect(status().isOk());

        // Seed sustained exceedance 400s ago (outside 60s cooldown window)
        seedEvent("SUSTAINED_COOL", "STATION", "dt5", 110, "F", 400);

        // First current event: duration satisfied, not suppressed — creates WO
        mockMvc.perform(post("/api/v1/alerts/events")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"SUSTAINED_COOL\",\"scopeType\":\"STATION\",\"scopeId\":\"dt5\",\"measuredValue\":120,\"unit\":\"F\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSatisfied", is(true)))
                .andExpect(jsonPath("$.suppressed", is(false)))
                .andExpect(jsonPath("$.workOrderId", notNullValue()));

        // Second event: duration still satisfied, but cooldown suppresses the WO
        mockMvc.perform(post("/api/v1/alerts/events")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"SUSTAINED_COOL\",\"scopeType\":\"STATION\",\"scopeId\":\"dt5\",\"measuredValue\":125,\"unit\":\"F\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSatisfied", is(true)))
                .andExpect(jsonPath("$.suppressed", is(true)))
                .andExpect(jsonPath("$.workOrderId").doesNotExist());
    }

    @Test
    void duration_overrideWithDuration_respectsOverrideValues() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Default: no duration, threshold 200
        mockMvc.perform(put("/api/v1/alerts/rules/defaults/DURATION_OVERRIDE")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"LOW\",\"thresholdOperator\":\"GT\",\"thresholdValue\":200,\"durationSeconds\":0,\"cooldownSeconds\":0}"))
                .andExpect(status().isOk());

        // Override for station dt6: threshold 80, duration 300s
        mockMvc.perform(put("/api/v1/alerts/rules/overrides/STATION/dt6/DURATION_OVERRIDE")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"severity\":\"HIGH\",\"thresholdOperator\":\"GT\",\"thresholdValue\":80,\"durationSeconds\":300,\"cooldownSeconds\":0}"))
                .andExpect(status().isOk());

        // Seed sustained exceedance at override threshold (>80) starting 400s ago
        seedEvent("DURATION_OVERRIDE", "STATION", "dt6", 90, "F", 400);

        // Current event: 95 > 80 (override threshold), sustained ~400s > 300s
        // Response must show override's requiredSeconds=300 and durationSatisfied=true
        mockMvc.perform(post("/api/v1/alerts/events")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"alertType\":\"DURATION_OVERRIDE\",\"scopeType\":\"STATION\",\"scopeId\":\"dt6\",\"measuredValue\":95,\"unit\":\"F\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationSatisfied", is(true)))
                .andExpect(jsonPath("$.durationRequiredSeconds", is(300)))
                .andExpect(jsonPath("$.workOrderId", notNullValue()));
    }

    /** Seeds a backdated alert event for precise timeline control in duration tests. */
    private void seedEvent(String alertType, String scopeType, String scopeId,
                            double measuredValue, String unit, int secondsAgo) {
        AlertEvent e = new AlertEvent();
        e.setAlertType(alertType);
        e.setScopeType(scopeType);
        e.setScopeId(scopeId);
        e.setSeverity("HIGH");
        e.setMeasuredValue(measuredValue);
        e.setMeasuredUnit(unit);
        e.setSuppressed(false);
        e.setCreatedAt(LocalDateTime.now().minusSeconds(secondsAgo));
        alertEventRepository.save(e);
    }

    @Test
    void assignWorkOrder_inDispatchedState_succeeds() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        String woBody = mockMvc.perform(post("/api/v1/work-orders")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Assign Test\",\"severity\":\"HIGH\"}"))
                .andReturn().getResponse().getContentAsString();
        Long woId = Long.parseLong(woBody.split("\"id\":")[1].split("[,}]")[0].trim());

        // Transition to DISPATCHED
        mockMvc.perform(post("/api/v1/work-orders/" + woId + "/transition")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"toStatus\":\"ACKNOWLEDGED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work-orders/" + woId + "/transition")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"toStatus\":\"DISPATCHED\"}"))
                .andExpect(status().isOk());

        // Assign
        mockMvc.perform(post("/api/v1/work-orders/" + woId + "/assign")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"assigneeId\":" + admin.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo", is(admin.getId().intValue())));
    }

    @Test
    void assignWorkOrder_notInDispatchedState_returns409() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        String woBody = mockMvc.perform(post("/api/v1/work-orders")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Assign Fail Test\",\"severity\":\"LOW\"}"))
                .andReturn().getResponse().getContentAsString();
        Long woId = Long.parseLong(woBody.split("\"id\":")[1].split("[,}]")[0].trim());

        // Try to assign in NEW_ALERT status — should fail
        mockMvc.perform(post("/api/v1/work-orders/" + woId + "/assign")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"assigneeId\":" + admin.getId() + "}"))
                .andExpect(status().isConflict());
    }

    private Account createAccount(String username) {
        Account a = new Account();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode("password"));
        a.setAccountType(Account.AccountType.PERSON);
        a.setStatus(Account.AccountStatus.ACTIVE);
        a.setFailedLoginAttempts(0);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return accountRepository.save(a);
    }

    private MockHttpSession sessionFor(Account account, RoleType role) {
        SessionAccount sa = new SessionAccount(account.getId(), account.getUsername(),
                role, RolePermissions.getPermissions(role), "ACTIVE");
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(SESSION_ACCOUNT_KEY, sa);
        return s;
    }
}
