package com.croh.audit;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
import com.croh.events.Event;
import com.croh.events.EventRegistration;
import com.croh.events.EventRegistrationRepository;
import com.croh.events.EventRepository;
import com.croh.reporting.ReportExecution;
import com.croh.reporting.ReportExecutionRepository;
import com.croh.reporting.ReportTemplate;
import com.croh.reporting.ReportTemplateRepository;
import com.croh.resources.ClaimRecordRepository;
import com.croh.resources.DownloadRecordRepository;
import com.croh.resources.PrintableNoticeRepository;
import com.croh.resources.ResourceItemRepository;
import com.croh.resources.UsagePolicyRepository;
import com.croh.rewards.FulfillmentExceptionRepository;
import com.croh.rewards.RewardItemRepository;
import com.croh.rewards.RewardOrderRepository;
import com.croh.rewards.ShippingAddressRepository;
import com.croh.security.RolePermissions;
import com.croh.security.RoleType;
import com.croh.security.SessionAccount;
import com.croh.verification.OrganizationCredentialDocumentRepository;
import com.croh.verification.PersonVerification;
import com.croh.verification.PersonVerificationRepository;
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

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditFixesRound2IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private RoleMembershipRepository roleMembershipRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventRegistrationRepository registrationRepository;
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
    @Autowired private PersonVerificationRepository personVerificationRepository;
    @Autowired private OrganizationCredentialDocumentRepository orgDocRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account orgUser;       // ORGANIZATION type, org_77 scope
    private Account otherOrgUser;  // ORGANIZATION type, org_99 scope
    private Account personUser;    // PERSON type
    private Account admin;         // PERSON type, ADMIN role

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
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        orgDocRepository.deleteAll();
        personVerificationRepository.deleteAll();
        addressRepository.deleteAll();
        roleMembershipRepository.deleteAll();
        accountRepository.deleteAll();

        orgUser = createAccount("orguser1", Account.AccountType.ORGANIZATION);
        otherOrgUser = createAccount("orguser2", Account.AccountType.ORGANIZATION);
        personUser = createAccount("personuser", Account.AccountType.PERSON);
        admin = createAccount("admin", Account.AccountType.PERSON);

        createRoleMembership(orgUser.getId(), "ORG_OPERATOR", "org_77");
        createRoleMembership(otherOrgUser.getId(), "ORG_OPERATOR", "org_99");
    }

    // ---- Issue 1: Cross-org data isolation ----

    @Test
    void issue1_pendingRegistrations_scopedToActorOrg() throws Exception {
        // Create events in two different orgs
        Event event77 = createEvent("org_77", orgUser.getId());
        Event event99 = createEvent("org_99", otherOrgUser.getId());

        // Create pending registrations for both
        createRegistration(event77.getId(), personUser.getId(), "PENDING_REVIEW");
        createRegistration(event99.getId(), personUser.getId(), "PENDING_REVIEW");

        // orgUser (org_77) should only see the registration for event in org_77
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        mockMvc.perform(get("/api/v1/registrations/pending").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventId", is(event77.getId().intValue())));
    }

    @Test
    void issue1_decideRegistration_crossOrg_returns403() throws Exception {
        Event event99 = createEvent("org_99", otherOrgUser.getId());
        EventRegistration reg = createRegistration(event99.getId(), personUser.getId(), "PENDING_REVIEW");

        // orgUser (org_77) tries to decide on an org_99 registration
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        mockMvc.perform(post("/api/v1/registrations/" + reg.getId() + "/decision")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue1_admin_seesAllPendingRegistrations() throws Exception {
        Event event77 = createEvent("org_77", orgUser.getId());
        Event event99 = createEvent("org_99", otherOrgUser.getId());
        createRegistration(event77.getId(), personUser.getId(), "PENDING_REVIEW");
        createRegistration(event99.getId(), personUser.getId(), "PENDING_REVIEW");

        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);
        mockMvc.perform(get("/api/v1/registrations/pending").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ---- Issue 2: Organization account-type enforcement ----

    @Test
    void issue2_personAccount_cannotSubmitOrgCredential() throws Exception {
        MockHttpSession session = sessionFor(personUser, RoleType.PARTICIPANT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "cred.pdf", "application/pdf", "fake-pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/verification/org-documents")
                        .file(file)
                        .with(csrf())
                        .session(session)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isForbidden());
    }

    @Test
    void issue2_orgAccount_canSubmitOrgCredential() throws Exception {
        MockHttpSession session = sessionFor(orgUser, RoleType.ORG_OPERATOR);
        MockMultipartFile file = new MockMultipartFile(
                "file", "cred.pdf", "application/pdf", "fake-pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/verification/org-documents")
                        .file(file)
                        .with(csrf())
                        .session(session)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isCreated());
    }

    @Test
    void issue2_personAccount_cannotGetOrgOperatorApproved() throws Exception {
        // Set up person verification and org credential (fake) for person account
        PersonVerification pv = new PersonVerification();
        pv.setAccountId(personUser.getId());
        pv.setLegalName("Test");
        pv.setDobEncrypted("enc");
        pv.setStatus("APPROVED");
        pv.setCreatedAt(LocalDateTime.now());
        pv.setUpdatedAt(LocalDateTime.now());
        personVerificationRepository.save(pv);

        // Create ORG_OPERATOR role request for person account
        RoleMembership rm = new RoleMembership();
        rm.setAccountId(personUser.getId());
        rm.setRoleType("ORG_OPERATOR");
        rm.setScopeId("org_77");
        rm.setStatus(RoleMembership.RoleMembershipStatus.REQUESTED);
        rm.setCreatedAt(LocalDateTime.now());
        rm.setUpdatedAt(LocalDateTime.now());
        RoleMembership saved = roleMembershipRepository.save(rm);

        // Admin tries to approve — should fail because PERSON account
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);
        mockMvc.perform(post("/api/v1/admin/roles/" + saved.getId() + "/decision")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- Issue 3: Custom registration form validation ----

    @Test
    void issue3_register_withCustomSchema_validatesRequiredFields() throws Exception {
        String schema = "[{\"id\":\"q1\",\"type\":\"text\",\"label\":\"Emergency Contact\",\"required\":true}]";
        Event event = createEventWithSchema("org_77", orgUser.getId(), schema);

        MockHttpSession session = sessionFor(personUser, RoleType.PARTICIPANT);

        // Missing required field — should fail
        mockMvc.perform(post("/api/v1/events/" + event.getId() + "/registrations")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"formResponses\":\"{}\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Required field missing")));
    }

    @Test
    void issue3_register_withCustomSchema_acceptsValidResponses() throws Exception {
        String schema = "[{\"id\":\"q1\",\"type\":\"text\",\"label\":\"Emergency Contact\",\"required\":true}]";
        Event event = createEventWithSchema("org_77", orgUser.getId(), schema);

        MockHttpSession session = sessionFor(personUser, RoleType.PARTICIPANT);

        mockMvc.perform(post("/api/v1/events/" + event.getId() + "/registrations")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"formResponses\":\"{\\\"q1\\\":\\\"John 555-1234\\\"}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void issue3_register_noSchema_acceptsAnyResponses() throws Exception {
        Event event = createEvent("org_77", orgUser.getId());

        MockHttpSession session = sessionFor(personUser, RoleType.PARTICIPANT);

        mockMvc.perform(post("/api/v1/events/" + event.getId() + "/registrations")
                        .with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"formResponses\":\"{}\"}"))
                .andExpect(status().isCreated());
    }

    // ---- Issue 5: Data-quality missing-rate and anomaly-distribution ----

    @Test
    void issue5_dataQuality_returnsMissingRateMetrics() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        mockMvc.perform(get("/api/v1/reports/data-quality")
                        .session(session)
                        .param("from", "2020-01-01T00:00:00")
                        .param("to", "2030-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events_missing_description.missing").isNumber())
                .andExpect(jsonPath("$.events_missing_description.total").isNumber())
                .andExpect(jsonPath("$.events_missing_description.rate").isNumber())
                .andExpect(jsonPath("$.failed_login_anomaly_distribution.low_1_4").isNumber())
                .andExpect(jsonPath("$.failed_login_anomaly_distribution.medium_5_9").isNumber())
                .andExpect(jsonPath("$.failed_login_anomaly_distribution.high_10_plus").isNumber());
    }

    // ---- Helpers ----

    private Account createAccount(String username, Account.AccountType type) {
        Account a = new Account();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode("password"));
        a.setAccountType(type);
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

    private Event createEvent(String orgId, Long createdBy) {
        return createEventWithSchema(orgId, createdBy, null);
    }

    private Event createEventWithSchema(String orgId, Long createdBy, String schema) {
        Event e = new Event();
        e.setOrganizationId(orgId);
        e.setTitle("Test Event " + orgId);
        e.setMode("ON_SITE");
        e.setStartAt(LocalDateTime.now().plusDays(1));
        e.setEndAt(LocalDateTime.now().plusDays(1).plusHours(2));
        e.setCapacity(50);
        e.setWaitlistEnabled(false);
        e.setManualReviewRequired(true);
        e.setRegistrationFormSchema(schema);
        e.setStatus("PUBLISHED");
        e.setCreatedBy(createdBy);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return eventRepository.save(e);
    }

    private EventRegistration createRegistration(Long eventId, Long accountId, String status) {
        EventRegistration reg = new EventRegistration();
        reg.setEventId(eventId);
        reg.setAccountId(accountId);
        reg.setStatus(status);
        reg.setCreatedAt(LocalDateTime.now());
        reg.setUpdatedAt(LocalDateTime.now());
        return registrationRepository.save(reg);
    }

    private MockHttpSession sessionFor(Account account, RoleType role) {
        SessionAccount sa = new SessionAccount(account.getId(), account.getUsername(),
                role, RolePermissions.getPermissions(role), "ACTIVE");
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(SESSION_ACCOUNT_KEY, sa);
        return s;
    }
}
