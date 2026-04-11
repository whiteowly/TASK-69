package com.croh.account;

import com.croh.audit.AuditLogRepository;
import com.croh.security.SessionAccount;
import com.croh.security.Permission;
import com.croh.security.RolePermissions;
import com.croh.security.RoleType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlacklistEnforcementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private BlacklistRecordRepository blacklistRecordRepository;
    @Autowired private BlacklistAppealRepository appealRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account userAccount;
    private Account adminAccount;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        appealRepository.deleteAll();
        blacklistRecordRepository.deleteAll();
        accountRepository.deleteAll();

        userAccount = createAccount("testuser", Account.AccountStatus.ACTIVE);
        adminAccount = createAccount("adminuser", Account.AccountStatus.ACTIVE);
    }

    @Test
    void blacklistedUser_blockedFromProtectedEndpoints_returns423() throws Exception {
        // Login the user
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession userSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Blacklist the user directly in DB (simulating admin action)
        userAccount.setStatus(Account.AccountStatus.BLACKLISTED);
        accountRepository.save(userAccount);

        // User's existing session should now be blocked on next request
        mockMvc.perform(get("/api/v1/auth/me")
                        .session(userSession))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code", is("ACCOUNT_BLACKLISTED")));
    }

    @Test
    void blacklistedUser_canStillSubmitAppeal() throws Exception {
        // Blacklist user and create record
        userAccount.setStatus(Account.AccountStatus.BLACKLISTED);
        accountRepository.save(userAccount);

        BlacklistRecord record = new BlacklistRecord();
        record.setAccountId(userAccount.getId());
        record.setReasonCode("SAFETY_POLICY_BREACH");
        record.setNote("test");
        record.setCreatedBy(adminAccount.getId());
        record.setCreatedAt(LocalDateTime.now());
        blacklistRecordRepository.save(record);

        // Create a session for the blacklisted user
        MockHttpSession session = createSessionFor(userAccount, RoleType.PARTICIPANT);

        // Appeal submission should be allowed (exempt from blacklist filter)
        mockMvc.perform(post("/api/v1/appeals")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blacklistId\":" + record.getId() + ",\"appealText\":\"Please review my case\",\"contactNote\":\"desk hours\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void blacklistedUser_canStillLogout() throws Exception {
        userAccount.setStatus(Account.AccountStatus.BLACKLISTED);
        accountRepository.save(userAccount);

        // Login first to get session + CSRF token
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"password123\"}"))
                .andExpect(status().is(423)) // login itself checks blacklist
                .andReturn();

        // Even if login blocked, test with a manually created session
        MockHttpSession session = createSessionFor(userAccount, RoleType.PARTICIPANT);
        Cookie csrfCookie = getCsrfCookie(session);

        mockMvc.perform(post("/api/v1/auth/logout")
                                .with(csrf())
                        .session(session)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminBlacklist_endpoint_blacklistsAccount() throws Exception {
        MockHttpSession adminSession = createSessionFor(adminAccount, RoleType.ADMIN);

        mockMvc.perform(post("/api/v1/admin/blacklist")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetAccountId\":" + userAccount.getId() + ",\"reasonCode\":\"SAFETY_POLICY_BREACH\",\"note\":\"test\"}"))
                .andExpect(status().isCreated());

        Account reloaded = accountRepository.findById(userAccount.getId()).orElseThrow();
        assert reloaded.getStatus() == Account.AccountStatus.BLACKLISTED;
    }

    @Test
    void adminBlacklist_requiresManageBlacklistPermission() throws Exception {
        // Participant does not have MANAGE_BLACKLIST
        MockHttpSession participantSession = createSessionFor(userAccount, RoleType.PARTICIPANT);

        mockMvc.perform(post("/api/v1/admin/blacklist")
                                .with(csrf())
                        .session(participantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "test-token")
                        .cookie(new Cookie("XSRF-TOKEN", "test-token"))
                        .content("{\"targetAccountId\":" + adminAccount.getId() + ",\"reasonCode\":\"TEST\",\"note\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void appealDecision_approveUnblock_restoresAccount() throws Exception {
        // Blacklist and create appeal
        userAccount.setStatus(Account.AccountStatus.BLACKLISTED);
        accountRepository.save(userAccount);

        BlacklistRecord record = new BlacklistRecord();
        record.setAccountId(userAccount.getId());
        record.setReasonCode("TEST");
        record.setNote("test");
        record.setCreatedBy(adminAccount.getId());
        record.setCreatedAt(LocalDateTime.now());
        blacklistRecordRepository.save(record);

        BlacklistAppeal appeal = new BlacklistAppeal();
        appeal.setBlacklistRecordId(record.getId());
        appeal.setAccountId(userAccount.getId());
        appeal.setAppealText("Please review");
        appeal.setStatus("PENDING");
        appeal.setDueDate(java.time.LocalDate.now().plusDays(3));
        appeal.setCreatedAt(LocalDateTime.now());
        appealRepository.save(appeal);

        MockHttpSession adminSession = createSessionFor(adminAccount, RoleType.ADMIN);

        mockMvc.perform(post("/api/v1/admin/appeals/" + appeal.getId() + "/decision")
                                .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "test-token")
                        .cookie(new Cookie("XSRF-TOKEN", "test-token"))
                        .content("{\"decision\":\"APPROVE_UNBLOCK\",\"decisionNote\":\"Appeal approved\"}"))
                .andExpect(status().isOk());

        Account reloaded = accountRepository.findById(userAccount.getId()).orElseThrow();
        assert reloaded.getStatus() == Account.AccountStatus.ACTIVE;
    }

    private Account createAccount(String username, Account.AccountStatus status) {
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode("password123"));
        account.setAccountType(Account.AccountType.PERSON);
        account.setStatus(status);
        account.setFailedLoginAttempts(0);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return accountRepository.save(account);
    }

    private MockHttpSession createSessionFor(Account account, RoleType role) {
        SessionAccount sa = new SessionAccount(
                account.getId(),
                account.getUsername(),
                role,
                RolePermissions.getPermissions(role),
                account.getStatus().name()
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_ACCOUNT_KEY, sa);
        return session;
    }

    private Cookie getCsrfCookie(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        return cookie != null ? cookie : new Cookie("XSRF-TOKEN", "fallback");
    }
}
