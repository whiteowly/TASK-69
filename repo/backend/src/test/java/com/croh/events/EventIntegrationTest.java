package com.croh.events;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventRegistrationRepository registrationRepository;
    @Autowired private RoleMembershipRepository roleMembershipRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account orgUser;
    private Account participant;
    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        roleMembershipRepository.deleteAll();
        accountRepository.deleteAll();
        orgUser = createAccount("orguser");
        participant = createAccount("participant");
        admin = createAccount("admin");

        // Create approved ORG_OPERATOR membership for orgUser scoped to org1
        RoleMembership rm = new RoleMembership();
        rm.setAccountId(orgUser.getId());
        rm.setRoleType("ORG_OPERATOR");
        rm.setScopeId("org1");
        rm.setStatus(RoleMembership.RoleMembershipStatus.APPROVED);
        rm.setCreatedAt(LocalDateTime.now());
        rm.setUpdatedAt(LocalDateTime.now());
        roleMembershipRepository.save(rm);
    }

    @Test
    void createEvent_withPublishPermission_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                                .with(csrf())
                        .session(sessionFor(orgUser, RoleType.ORG_OPERATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Aid Night\",\"mode\":\"ON_SITE\",\"location\":\"Hall A\",\"startAt\":\"2026-05-01T17:00:00Z\",\"endAt\":\"2026-05-01T19:00:00Z\",\"organizationId\":\"org1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Aid Night")))
                .andExpect(jsonPath("$.capacity", is(50)));
    }

    @Test
    void createEvent_withoutPermission_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                                .with(csrf())
                        .session(sessionFor(participant, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Test\",\"mode\":\"ON_SITE\",\"startAt\":\"2026-05-01T17:00:00Z\",\"endAt\":\"2026-05-01T19:00:00Z\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_autoApproves_whenNoManualReview() throws Exception {
        Long eventId = createTestEvent(false, false, 50);

        mockMvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                                .with(csrf())
                        .session(sessionFor(participant, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    void register_pendingReview_whenManualReviewRequired() throws Exception {
        Long eventId = createTestEvent(true, false, 50);

        mockMvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                                .with(csrf())
                        .session(sessionFor(participant, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING_REVIEW")));
    }

    @Test
    void register_waitlisted_whenFull() throws Exception {
        Long eventId = createTestEvent(false, true, 1);
        // Fill the one seat
        registerUser(eventId, participant);

        Account second = createAccount("second");
        mockMvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                                .with(csrf())
                        .session(sessionFor(second, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("WAITLISTED")));
    }

    @Test
    void cancel_promotesOldestWaitlisted() throws Exception {
        Long eventId = createTestEvent(false, true, 1);
        // Fill seat
        Long regId = registerUser(eventId, participant);

        // Waitlist two
        Account w1 = createAccount("wait1");
        Account w2 = createAccount("wait2");
        registerUser(eventId, w1);
        registerUser(eventId, w2);

        // Cancel the approved one
        mockMvc.perform(post("/api/v1/registrations/" + regId + "/cancel")
                                .with(csrf())
                        .session(sessionFor(participant, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isOk());

        // w1 should be promoted
        EventRegistration w1Reg = registrationRepository.findByAccountId(w1.getId()).get(0);
        assert "APPROVED".equals(w1Reg.getStatus());
    }

    @Test
    void roster_returnsApprovedRegistrations() throws Exception {
        Long eventId = createTestEvent(false, false, 50);
        registerUser(eventId, participant);

        mockMvc.perform(get("/api/v1/events/" + eventId + "/roster")
                        .session(sessionFor(admin, RoleType.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void exportRoster_csv_createsLocalFile() throws Exception {
        Long eventId = createTestEvent(false, false, 50);
        registerUser(eventId, participant);

        mockMvc.perform(post("/api/v1/events/" + eventId + "/roster/export")
                                .with(csrf())
                        .session(sessionFor(admin, RoleType.ADMIN))
                        .param("format", "CSV")
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportFilePath", org.hamcrest.Matchers.containsString(".csv")))
                .andExpect(jsonPath("$.format", is("CSV")));
    }

    @Test
    void exportRoster_pdf_createsLocalFile() throws Exception {
        Long eventId = createTestEvent(false, false, 50);
        registerUser(eventId, participant);

        mockMvc.perform(post("/api/v1/events/" + eventId + "/roster/export")
                                .with(csrf())
                        .session(sessionFor(admin, RoleType.ADMIN))
                        .param("format", "PDF")
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportFilePath", org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(jsonPath("$.format", is("PDF")));
    }

    @Test
    void exportRoster_requiresPermission() throws Exception {
        Long eventId = createTestEvent(false, false, 50);

        mockMvc.perform(post("/api/v1/events/" + eventId + "/roster/export")
                                .with(csrf())
                        .session(sessionFor(participant, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isForbidden());
    }

    private Long createTestEvent(boolean manualReview, boolean waitlist, int capacity) throws Exception {
        String json = String.format(
                "{\"title\":\"Test Event\",\"mode\":\"ON_SITE\",\"startAt\":\"2026-05-01T17:00:00Z\",\"endAt\":\"2026-05-01T19:00:00Z\",\"capacity\":%d,\"waitlistEnabled\":%s,\"manualReviewRequired\":%s,\"organizationId\":\"org1\"}",
                capacity, waitlist, manualReview);
        String body = mockMvc.perform(post("/api/v1/events")
                                .with(csrf())
                        .session(sessionFor(orgUser, RoleType.ORG_OPERATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content(json))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.split("\"id\":")[1].split("[,}]")[0].trim());
    }

    private Long registerUser(Long eventId, Account user) throws Exception {
        String body = mockMvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.split("\"id\":")[1].split("[,}]")[0].trim());
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
