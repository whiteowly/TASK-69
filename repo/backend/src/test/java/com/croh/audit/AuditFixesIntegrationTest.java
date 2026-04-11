package com.croh.audit;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
import com.croh.reporting.ReportExecution;
import com.croh.reporting.ReportExecutionRepository;
import com.croh.reporting.ReportTemplate;
import com.croh.reporting.ReportTemplateRepository;
import com.croh.resources.DownloadRecord;
import com.croh.resources.DownloadRecordRepository;
import com.croh.resources.PrintableNoticeRepository;
import com.croh.resources.ClaimRecordRepository;
import com.croh.resources.ResourceItem;
import com.croh.resources.ResourceItemRepository;
import com.croh.resources.UsagePolicy;
import com.croh.resources.UsagePolicyRepository;
import com.croh.rewards.RewardItem;
import com.croh.rewards.RewardItemRepository;
import com.croh.rewards.RewardOrder;
import com.croh.rewards.RewardOrderRepository;
import com.croh.rewards.FulfillmentExceptionRepository;
import com.croh.rewards.ShippingAddressRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for audit fixes #1-#8.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditFixesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private RoleMembershipRepository roleMembershipRepository;
    @Autowired private ResourceItemRepository resourceRepository;
    @Autowired private UsagePolicyRepository policyRepository;
    @Autowired private DownloadRecordRepository downloadRecordRepository;
    @Autowired private ClaimRecordRepository claimRecordRepository;
    @Autowired private PrintableNoticeRepository noticeRepository;
    @Autowired private ShippingAddressRepository addressRepository;
    @Autowired private RewardItemRepository rewardRepository;
    @Autowired private RewardOrderRepository orderRepository;
    @Autowired private FulfillmentExceptionRepository exceptionRepository;
    @Autowired private ReportExecutionRepository executionRepository;
    @Autowired private ReportTemplateRepository templateRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account orgUser;
    private Account otherOrgUser;
    private Account participant;
    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        executionRepository.deleteAll();
        templateRepository.deleteAll();
        exceptionRepository.deleteAll();
        orderRepository.deleteAll();
        rewardRepository.deleteAll();
        noticeRepository.deleteAll();
        claimRecordRepository.deleteAll();
        downloadRecordRepository.deleteAll();
        resourceRepository.deleteAll();
        policyRepository.deleteAll();
        addressRepository.deleteAll();
        roleMembershipRepository.deleteAll();
        accountRepository.deleteAll();

        orgUser = createAccount("orguser1");
        otherOrgUser = createAccount("orguser2");
        participant = createAccount("participant");
        admin = createAccount("admin");

        // org_77 scoped membership for orgUser
        createRoleMembership(orgUser.getId(), "ORG_OPERATOR", "org_77");
        // org_99 scoped membership for otherOrgUser
        createRoleMembership(otherOrgUser.getId(), "ORG_OPERATOR", "org_99");
    }

    // ---- Issue 1: Resource file upload trust boundary ----

    @Test
    void issue1_uploadResource_multipartWithValidPdf_succeeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.pdf", "application/pdf", "PDF-content".getBytes());
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);

        mockMvc.perform(multipart("/api/v1/resources/upload")
                        .file(file)
                        .param("title", "Storm Guide")
                        .param("fileVersion", "v1")
                        .param("organizationId", "org_77")
                        .with(csrf())
                        .session(session)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", is("DOWNLOADABLE_FILE")))
                .andExpect(jsonPath("$.fileSize").isNumber())
                .andExpect(jsonPath("$.contentType", is("application/pdf")));
    }

    @Test
    void issue1_uploadResource_invalidFileType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/x-msdownload", "binary".getBytes());
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);

        mockMvc.perform(multipart("/api/v1/resources/upload")
                        .file(file)
                        .param("title", "Bad File")
                        .param("organizationId", "org_77")
                        .with(csrf())
                        .session(session)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("File type not allowed")));
    }

    @Test
    void issue1_publishResource_noClientFilePath_accepted() throws Exception {
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);

        // JSON publish for CLAIMABLE_ITEM — no filePath field at all
        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"type\":\"CLAIMABLE_ITEM\",\"title\":\"Kit\",\"inventoryCount\":5,\"organizationId\":\"org_77\"}"))
                .andExpect(status().isCreated());
    }

    // ---- Issue 2: SecurityException -> 403 ----

    @Test
    void issue2_securityException_returns403_notUpdateOwnEvent() throws Exception {
        // Create event as orgUser
        MockHttpSession orgSession = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        String eventJson = "{\"organizationId\":\"org_77\",\"title\":\"Test\",\"startAt\":\"2026-05-01T10:00:00\",\"endAt\":\"2026-05-01T12:00:00\"}";
        String createResult = mockMvc.perform(post("/api/v1/events")
                        .with(csrf())
                        .session(orgSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content(eventJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract event ID
        Long eventId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(createResult).get("id").asLong();

        // Try to update as otherOrgUser (not the creator) — should return 403
        MockHttpSession otherSession = sessionFor(otherOrgUser, RoleType.ORG_OPERATOR);
        mockMvc.perform(patch("/api/v1/events/" + eventId)
                        .with(csrf())
                        .session(otherSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue2_cancelOtherUserRegistration_returns403() throws Exception {
        // Create event
        MockHttpSession orgSession = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        String eventJson = "{\"organizationId\":\"org_77\",\"title\":\"Test Event\",\"startAt\":\"2026-05-01T10:00:00\",\"endAt\":\"2026-05-01T12:00:00\"}";
        String createResult = mockMvc.perform(post("/api/v1/events")
                        .with(csrf()).session(orgSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content(eventJson))
                .andReturn().getResponse().getContentAsString();
        Long eventId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(createResult).get("id").asLong();

        // Register participant
        MockHttpSession partSession = sessionFor(participant, RoleType.PARTICIPANT);
        String regResult = mockMvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .with(csrf()).session(partSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"formResponses\":\"\"}"))
                .andReturn().getResponse().getContentAsString();
        Long regId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(regResult).get("id").asLong();

        // otherOrgUser tries to cancel participant's registration
        MockHttpSession otherSession = sessionFor(otherOrgUser, RoleType.ORG_OPERATOR);
        mockMvc.perform(post("/api/v1/registrations/" + regId + "/cancel")
                        .with(csrf()).session(otherSession)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isForbidden());
    }

    // ---- Issue 3: Per-file-version download limits ----

    @Test
    void issue3_downloadLimit_perVersion_independent() throws Exception {
        Long policyId = createPolicy("USER", 1, 30, "DOWNLOAD");
        Long resId = createResource("DOWNLOADABLE_FILE", null, policyId);
        MockHttpSession session = sessionFor(participant, RoleType.PARTICIPANT);

        // Download v1 — allowed
        mockMvc.perform(post("/api/v1/resources/files/" + resId + "/download")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"fileVersion\":\"v1\"}"))
                .andExpect(jsonPath("$.result", is("ALLOWED")));

        // Download v1 again — denied (limit 1)
        mockMvc.perform(post("/api/v1/resources/files/" + resId + "/download")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"fileVersion\":\"v1\"}"))
                .andExpect(jsonPath("$.result", is("DENIED_POLICY")));

        // Download v2 — allowed (different version, independent count)
        mockMvc.perform(post("/api/v1/resources/files/" + resId + "/download")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"fileVersion\":\"v2\"}"))
                .andExpect(jsonPath("$.result", is("ALLOWED")));
    }

    // ---- Issue 4: Organization/tenant isolation ----

    @Test
    void issue4_createEvent_wrongOrg_returns403() throws Exception {
        // orgUser is authorized for org_77 but tries to publish under org_99
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        String json = "{\"organizationId\":\"org_99\",\"title\":\"Cross-org attack\",\"startAt\":\"2026-05-01T10:00:00\",\"endAt\":\"2026-05-01T12:00:00\"}";

        mockMvc.perform(post("/api/v1/events")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue4_createEvent_ownOrg_succeeds() throws Exception {
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        String json = "{\"organizationId\":\"org_77\",\"title\":\"Legit Event\",\"startAt\":\"2026-05-01T10:00:00\",\"endAt\":\"2026-05-01T12:00:00\"}";

        mockMvc.perform(post("/api/v1/events")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content(json))
                .andExpect(status().isCreated());
    }

    @Test
    void issue4_publishResource_wrongOrg_returns403() throws Exception {
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        String json = "{\"type\":\"CLAIMABLE_ITEM\",\"title\":\"Cross-org\",\"inventoryCount\":5,\"organizationId\":\"org_99\"}";

        mockMvc.perform(post("/api/v1/resources")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue4_admin_canPublishAnyOrg() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);
        String json = "{\"organizationId\":\"org_99\",\"title\":\"Admin Event\",\"startAt\":\"2026-05-01T10:00:00\",\"endAt\":\"2026-05-01T12:00:00\"}";

        mockMvc.perform(post("/api/v1/events")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content(json))
                .andExpect(status().isCreated());
    }

    // ---- Issue 5: Analytics dimensions ----

    @Test
    void issue5_operationsSummary_includesNewDimensions() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        mockMvc.perform(get("/api/v1/analytics/operations-summary")
                        .session(session)
                        .param("from", "2020-01-01T00:00:00")
                        .param("to", "2030-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffWorkload").exists())
                .andExpect(jsonPath("$.popularCategories").exists())
                .andExpect(jsonPath("$.retentionRate").isNumber())
                .andExpect(jsonPath("$.registrationApprovalRate").isNumber())
                .andExpect(jsonPath("$.orderCompletionRate").isNumber());
    }

    // ---- Issue 6: Overdue detection with status-age semantics ----

    @Test
    void issue6_overdueOrders_usesStatusChangedAt() throws Exception {
        RewardItem reward = createRewardItem();
        RewardOrder order = new RewardOrder();
        order.setRewardId(reward.getId());
        order.setAccountId(participant.getId());
        order.setQuantity(1);
        order.setFulfillmentType("PHYSICAL_SHIPMENT");
        order.setStatus("PACKED");
        // Status changed 10 days ago
        order.setStatusChangedAt(LocalDateTime.now().minusDays(10));
        order.setCreatedAt(LocalDateTime.now().minusDays(15));
        order.setUpdatedAt(LocalDateTime.now().minusDays(1)); // updated recently (e.g. tracking set)
        orderRepository.save(order);

        List<RewardOrder> overdue = orderRepository.findOverdueOrders(
                List.of("PACKED", "SHIPPED"), LocalDateTime.now().minusDays(7));
        assertEquals(1, overdue.size());
        assertEquals(order.getId(), overdue.get(0).getId());
    }

    @Test
    void issue6_notOverdue_whenStatusChangedRecently() throws Exception {
        RewardItem reward = createRewardItem();
        RewardOrder order = new RewardOrder();
        order.setRewardId(reward.getId());
        order.setAccountId(participant.getId());
        order.setQuantity(1);
        order.setFulfillmentType("PHYSICAL_SHIPMENT");
        order.setStatus("PACKED");
        // Status changed 2 days ago (not overdue)
        order.setStatusChangedAt(LocalDateTime.now().minusDays(2));
        order.setCreatedAt(LocalDateTime.now().minusDays(20)); // old creation
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        List<RewardOrder> overdue = orderRepository.findOverdueOrders(
                List.of("PACKED", "SHIPPED"), LocalDateTime.now().minusDays(7));
        assertTrue(overdue.isEmpty());
    }

    // ---- Issue 7: Report execution visibility ----

    @Test
    void issue7_listExecutions_scopedToOwner() throws Exception {
        // Create executions for different users
        ReportTemplate template = createTemplate();
        createExecution(template.getId(), orgUser.getId());
        createExecution(template.getId(), admin.getId());

        // orgUser should only see their own
        MockHttpSession orgSession = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        mockMvc.perform(get("/api/v1/reports/executions")
                        .session(orgSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].resultData").doesNotExist());

        // admin sees all
        MockHttpSession adminSession = sessionFor(admin, RoleType.ADMIN);
        mockMvc.perform(get("/api/v1/reports/executions")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ---- Issue 8: Analytics date-time format ----

    @Test
    void issue8_analytics_acceptsIsoDateTime() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // ISO date-time format as sent by the fixed frontend
        mockMvc.perform(get("/api/v1/analytics/operations-summary")
                        .session(session)
                        .param("from", "2026-01-01T00:00:00")
                        .param("to", "2026-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistrations").isNumber());
    }

    @Test
    void issue8_analytics_validIsoDateTimeParams_succeeds() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Correct ISO date-time format (as fixed frontend now sends)
        mockMvc.perform(get("/api/v1/analytics/operations-summary")
                        .session(session)
                        .param("from", "2026-04-01T00:00:00")
                        .param("to", "2026-04-30T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistrations").isNumber());
    }

    // ---- Helpers ----

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

    private void createRoleMembership(Long accountId, String roleType, String scopeId) {
        RoleMembership rm = new RoleMembership();
        rm.setAccountId(accountId);
        rm.setRoleType(roleType);
        rm.setScopeId(scopeId);
        rm.setStatus(RoleMembership.RoleMembershipStatus.APPROVED);
        rm.setCreatedAt(LocalDateTime.now());
        rm.setUpdatedAt(LocalDateTime.now());
        roleMembershipRepository.save(rm);
    }

    private Long createPolicy(String scope, int maxActions, int windowDays, String action) {
        UsagePolicy p = new UsagePolicy();
        p.setName("test-" + scope);
        p.setScope(scope);
        p.setMaxActions(maxActions);
        p.setWindowDays(windowDays);
        p.setResourceAction(action);
        p.setCreatedAt(LocalDateTime.now());
        return policyRepository.save(p).getId();
    }

    private Long createResource(String type, Integer inventory, Long policyId) {
        ResourceItem r = new ResourceItem();
        r.setType(type);
        r.setTitle("Test Resource");
        r.setInventoryCount(inventory);
        r.setUsagePolicyId(policyId);
        r.setStatus("PUBLISHED");
        r.setCreatedBy(orgUser.getId());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return resourceRepository.save(r).getId();
    }

    private RewardItem createRewardItem() {
        RewardItem item = new RewardItem();
        item.setTitle("Test Reward");
        item.setInventoryCount(10);
        item.setPerUserLimit(5);
        item.setFulfillmentType("PHYSICAL_SHIPMENT");
        item.setStatus("ACTIVE");
        item.setCreatedBy(admin.getId());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return rewardRepository.save(item);
    }

    private ReportTemplate createTemplate() {
        ReportTemplate t = new ReportTemplate();
        t.setName("Test Template");
        t.setMetricIds("");
        t.setOutputFormat("CSV");
        t.setCreatedBy(admin.getId());
        t.setCreatedAt(LocalDateTime.now());
        return templateRepository.save(t);
    }

    private void createExecution(Long templateId, Long executedBy) {
        ReportExecution e = new ReportExecution();
        e.setTemplateId(templateId);
        e.setOutputFormat("CSV");
        e.setStatus("COMPLETED");
        e.setResultData("test data");
        e.setExecutedBy(executedBy);
        e.setCreatedAt(LocalDateTime.now());
        e.setCompletedAt(LocalDateTime.now());
        executionRepository.save(e);
    }

    private MockHttpSession sessionFor(Account account, RoleType role) {
        SessionAccount sa = new SessionAccount(account.getId(), account.getUsername(),
                role, RolePermissions.getPermissions(role), "ACTIVE");
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(SESSION_ACCOUNT_KEY, sa);
        return s;
    }
}
