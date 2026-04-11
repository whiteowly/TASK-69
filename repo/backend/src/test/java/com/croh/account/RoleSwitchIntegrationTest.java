package com.croh.account;

import com.croh.audit.AuditLogRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.security.RolePermissions;
import com.croh.security.RoleType;
import com.croh.security.SessionAccount;
import com.croh.verification.OrganizationCredentialDocument;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleSwitchIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private RoleMembershipRepository roleMembershipRepository;
    @Autowired private PersonVerificationRepository personVerificationRepository;
    @Autowired private OrganizationCredentialDocumentRepository orgDocRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account user;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        orgDocRepository.deleteAll();
        personVerificationRepository.deleteAll();
        roleMembershipRepository.deleteAll();
        accountRepository.deleteAll();
        user = createAccount("roleuser");
    }

    // === Basic role request/list ===

    @Test
    void requestRole_creates201() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/me/role-requests")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleType", is("VOLUNTEER")))
                .andExpect(jsonPath("$.status", is("REQUESTED")));
    }

    @Test
    void requestRole_duplicate_returns409() throws Exception {
        MockHttpSession session = sessionFor(user, RoleType.PARTICIPANT);
        mockMvc.perform(post("/api/v1/accounts/me/role-requests")
                                .with(csrf())
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/accounts/me/role-requests")
                                .with(csrf())
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listRoles_showsAllMemberships() throws Exception {
        createMembership(user.getId(), "VOLUNTEER", RoleMembership.RoleMembershipStatus.APPROVED);
        createMembership(user.getId(), "ADMIN", RoleMembership.RoleMembershipStatus.REQUESTED);

        mockMvc.perform(get("/api/v1/accounts/me/roles")
                        .session(sessionFor(user, RoleType.PARTICIPANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    // === Role switch only to approved ===

    @Test
    void switchRole_toUnapproved_returns409() throws Exception {
        createMembership(user.getId(), "ADMIN", RoleMembership.RoleMembershipStatus.REQUESTED);

        mockMvc.perform(put("/api/v1/accounts/me/active-role")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void switchRole_toNonexistent_returns409() throws Exception {
        mockMvc.perform(put("/api/v1/accounts/me/active-role")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"role\":\"ORG_OPERATOR\"}"))
                .andExpect(status().isConflict());
    }

    // === Verification prerequisites for role approval ===

    @Test
    void approveVolunteer_withoutPersonVerification_returns409() throws Exception {
        createMembership(user.getId(), "VOLUNTEER", RoleMembership.RoleMembershipStatus.REQUESTED);
        RoleMembership m = roleMembershipRepository.findByAccountId(user.getId()).get(0);

        MockHttpSession adminSession = sessionFor(createAccount("admin1"), RoleType.ADMIN);
        mockMvc.perform(post("/api/v1/admin/roles/" + m.getId() + "/decision")
                                .with(csrf())
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Person verification")));
    }

    @Test
    void approveVolunteer_withApprovedPersonVerification_succeeds() throws Exception {
        createApprovedPersonVerification(user.getId());
        createMembership(user.getId(), "VOLUNTEER", RoleMembership.RoleMembershipStatus.REQUESTED);
        RoleMembership m = roleMembershipRepository.findByAccountId(user.getId()).get(0);

        MockHttpSession adminSession = sessionFor(createAccount("admin2"), RoleType.ADMIN);
        mockMvc.perform(post("/api/v1/admin/roles/" + m.getId() + "/decision")
                                .with(csrf())
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    void approveOrgOperator_withoutOrgCredential_returns409() throws Exception {
        // Use ORGANIZATION account to pass account-type check, but without org credential
        Account orgAccount = createAccount("orgnocred", Account.AccountType.ORGANIZATION);
        createApprovedPersonVerification(orgAccount.getId());
        createMembership(orgAccount.getId(), "ORG_OPERATOR", RoleMembership.RoleMembershipStatus.REQUESTED);
        RoleMembership m = roleMembershipRepository.findByAccountId(orgAccount.getId()).get(0);

        MockHttpSession adminSession = sessionFor(createAccount("admin3"), RoleType.ADMIN);
        mockMvc.perform(post("/api/v1/admin/roles/" + m.getId() + "/decision")
                                .with(csrf())
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("organization credential")));
    }

    @Test
    void approveOrgOperator_withBothVerifications_succeeds() throws Exception {
        // ORG_OPERATOR requires ORGANIZATION account type
        Account orgAccount = createAccount("orgaccount", Account.AccountType.ORGANIZATION);
        createApprovedPersonVerification(orgAccount.getId());
        createApprovedOrgCredential(orgAccount.getId());
        createMembership(orgAccount.getId(), "ORG_OPERATOR", RoleMembership.RoleMembershipStatus.REQUESTED);
        RoleMembership m = roleMembershipRepository.findByAccountId(orgAccount.getId()).get(0);

        MockHttpSession adminSession = sessionFor(createAccount("admin4"), RoleType.ADMIN);
        mockMvc.perform(post("/api/v1/admin/roles/" + m.getId() + "/decision")
                                .with(csrf())
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    // === Verification prerequisites for role switch ===

    @Test
    void switchToVolunteer_withoutVerification_returns409() throws Exception {
        createMembership(user.getId(), "VOLUNTEER", RoleMembership.RoleMembershipStatus.APPROVED);
        // No person verification

        mockMvc.perform(put("/api/v1/accounts/me/active-role")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void switchToVolunteer_withVerification_succeeds() throws Exception {
        createApprovedPersonVerification(user.getId());
        createMembership(user.getId(), "VOLUNTEER", RoleMembership.RoleMembershipStatus.APPROVED);

        mockMvc.perform(put("/api/v1/accounts/me/active-role")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole", is("VOLUNTEER")));
    }

    // === Full flow: verify → approve → switch ===

    @Test
    void fullFlow_verifyThenApproveRoleThenSwitch() throws Exception {
        // 1. User gets verified
        createApprovedPersonVerification(user.getId());

        // 2. User requests VOLUNTEER
        createMembership(user.getId(), "VOLUNTEER", RoleMembership.RoleMembershipStatus.REQUESTED);
        RoleMembership m = roleMembershipRepository.findByAccountId(user.getId()).get(0);

        // 3. Admin approves role (prerequisites met)
        MockHttpSession adminSession = sessionFor(createAccount("admin5"), RoleType.ADMIN);
        mockMvc.perform(post("/api/v1/admin/roles/" + m.getId() + "/decision")
                                .with(csrf())
                        .session(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk());

        // 4. User switches to VOLUNTEER
        mockMvc.perform(put("/api/v1/accounts/me/active-role")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeRole", is("VOLUNTEER")));
    }

    // === Helpers ===

    private Account createAccount(String username) {
        return createAccount(username, Account.AccountType.PERSON);
    }

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

    private void createMembership(Long accountId, String role, RoleMembership.RoleMembershipStatus status) {
        RoleMembership m = new RoleMembership();
        m.setAccountId(accountId);
        m.setRoleType(role);
        m.setStatus(status);
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        roleMembershipRepository.save(m);
    }

    private void createApprovedPersonVerification(Long accountId) {
        PersonVerification pv = new PersonVerification();
        pv.setAccountId(accountId);
        pv.setLegalName("Test User");
        pv.setDobEncrypted("encrypted-dob");
        pv.setStatus("APPROVED");
        pv.setCreatedAt(LocalDateTime.now());
        pv.setUpdatedAt(LocalDateTime.now());
        personVerificationRepository.save(pv);
    }

    private void createApprovedOrgCredential(Long accountId) {
        OrganizationCredentialDocument doc = new OrganizationCredentialDocument();
        doc.setAccountId(accountId);
        doc.setFileName("cert.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(1024L);
        doc.setFilePath("test/path.enc");
        doc.setChecksum("abc123");
        doc.setDuplicateFlag(false);
        doc.setStatus("APPROVED");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        orgDocRepository.save(doc);
    }

    private MockHttpSession sessionFor(Account account, RoleType role) {
        SessionAccount sa = new SessionAccount(account.getId(), account.getUsername(),
                role, RolePermissions.getPermissions(role), "ACTIVE");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_ACCOUNT_KEY, sa);
        return session;
    }
}
