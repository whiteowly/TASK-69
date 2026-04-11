package com.croh.auth;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.croh.audit.AuditLog;
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
import java.util.List;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPasswordResetIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account targetAccount;
    private Account adminAccount;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();

        targetAccount = createAccount("target.user");
        adminAccount = createAccount("admin.user");
    }

    @Test
    void passwordReset_byAdmin_returns202AndResetsPassword() throws Exception {
        String oldHash = targetAccount.getPasswordHash();
        MockHttpSession adminSession = createAdminSession(adminAccount);

        mockMvc.perform(post("/api/v1/admin/password-resets")
                                .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "test-token")
                        .cookie(new Cookie("XSRF-TOKEN", "test-token"))
                        .content("{\"targetAccountId\":" + targetAccount.getId() + ",\"identityReviewNote\":\"Verified in person\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("ISSUED")))
                .andExpect(jsonPath("$.temporarySecretIssued", is(true)))
                .andExpect(jsonPath("$.temporarySecret", notNullValue()));

        Account reloaded = accountRepository.findById(targetAccount.getId()).orElseThrow();
        // Password hash must have changed
        assertTrue(!reloaded.getPasswordHash().equals(oldHash));
        assertEquals(0, reloaded.getFailedLoginAttempts());
    }

    @Test
    void passwordReset_emitsAuditLog() throws Exception {
        MockHttpSession adminSession = createAdminSession(adminAccount);

        mockMvc.perform(post("/api/v1/admin/password-resets")
                                .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "test-token")
                        .cookie(new Cookie("XSRF-TOKEN", "test-token"))
                        .content("{\"targetAccountId\":" + targetAccount.getId() + ",\"identityReviewNote\":\"Verified in person\"}"))
                .andExpect(status().isAccepted());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(l -> "PASSWORD_RESET".equals(l.getActionType())));
    }

    @Test
    void passwordReset_byNonAdmin_returns403() throws Exception {
        MockHttpSession participantSession = new MockHttpSession();
        SessionAccount sa = new SessionAccount(
                targetAccount.getId(), targetAccount.getUsername(),
                RoleType.PARTICIPANT, RolePermissions.getPermissions(RoleType.PARTICIPANT),
                "ACTIVE");
        participantSession.setAttribute(SESSION_ACCOUNT_KEY, sa);

        mockMvc.perform(post("/api/v1/admin/password-resets")
                                .with(csrf())
                        .session(participantSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "test-token")
                        .cookie(new Cookie("XSRF-TOKEN", "test-token"))
                        .content("{\"targetAccountId\":" + adminAccount.getId() + ",\"identityReviewNote\":\"note\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordReset_missingReviewNote_returns400() throws Exception {
        MockHttpSession adminSession = createAdminSession(adminAccount);

        mockMvc.perform(post("/api/v1/admin/password-resets")
                                .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "test-token")
                        .cookie(new Cookie("XSRF-TOKEN", "test-token"))
                        .content("{\"targetAccountId\":" + targetAccount.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordReset_thenLoginWithTempPassword_succeeds() throws Exception {
        MockHttpSession adminSession = createAdminSession(adminAccount);

        String responseBody = mockMvc.perform(post("/api/v1/admin/password-resets")
                                .with(csrf())
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "test-token")
                        .cookie(new Cookie("XSRF-TOKEN", "test-token"))
                        .content("{\"targetAccountId\":" + targetAccount.getId() + ",\"identityReviewNote\":\"Verified\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        // Extract temp password from response
        String tempPassword = responseBody.split("\"temporarySecret\":\"")[1].split("\"")[0];

        // Login with the temporary password
        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"target.user\",\"password\":\"" + tempPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("target.user")));
    }

    private Account createAccount(String username) {
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode("original-password"));
        account.setAccountType(Account.AccountType.PERSON);
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setFailedLoginAttempts(0);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return accountRepository.save(account);
    }

    private MockHttpSession createAdminSession(Account admin) {
        SessionAccount sa = new SessionAccount(
                admin.getId(), admin.getUsername(),
                RoleType.ADMIN, RolePermissions.getPermissions(RoleType.ADMIN),
                "ACTIVE");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_ACCOUNT_KEY, sa);
        return session;
    }
}
