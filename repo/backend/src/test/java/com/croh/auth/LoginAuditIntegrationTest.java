package com.croh.auth;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.croh.audit.AuditLog;
import com.croh.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests proving audit records are persisted for login lifecycle events.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginAuditIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void failedLogin_emitsLoginFailedAudit() throws Exception {
        Account account = createAccount("user1", Account.AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user1\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());

        AuditLog log = logs.get(0);
        assertEquals("LOGIN_FAILED", log.getActionType());
        assertEquals("ACCOUNT", log.getObjectType());
        assertEquals(account.getId().toString(), log.getObjectId());
        assertEquals("INVALID_PASSWORD", log.getReasonCode());
        assertNotNull(log.getCorrelationId());
        assertNull(log.getActorRole()); // no role for failed login
    }

    @Test
    void lockoutTriggered_emitsLockoutAudit_notLoginFailed() throws Exception {
        Account account = createAccount("user2", Account.AccountStatus.ACTIVE);
        account.setFailedLoginAttempts(9); // next failure triggers lockout
        accountRepository.save(account);

        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user2\",\"password\":\"wrong\"}"))
                .andExpect(status().is(423));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());

        AuditLog log = logs.get(0);
        assertEquals("ACCOUNT_LOCKOUT", log.getActionType());
        assertEquals("ACCOUNT", log.getObjectType());
        assertEquals(account.getId().toString(), log.getObjectId());
        assertEquals("ACTIVE", log.getBeforeState());
        assertEquals("LOCKED", log.getAfterState());
        assertEquals("MAX_FAILED_ATTEMPTS", log.getReasonCode());
    }

    @Test
    void blacklistedLogin_emitsBlacklistedAudit() throws Exception {
        Account account = createAccount("blocked1", Account.AccountStatus.BLACKLISTED);

        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked1\",\"password\":\"password123\"}"))
                .andExpect(status().is(423));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());

        AuditLog log = logs.get(0);
        assertEquals("LOGIN_BLACKLISTED", log.getActionType());
        assertEquals("ACCOUNT", log.getObjectType());
        assertEquals(account.getId().toString(), log.getObjectId());
        assertEquals("BLACKLISTED", log.getAfterState());
        assertEquals("CONSTRAINED_SESSION", log.getReasonCode());
    }

    @Test
    void successfulLogin_emitsNoAudit() throws Exception {
        createAccount("normal1", Account.AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"normal1\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.isEmpty(), "Successful login should not emit audit");
    }

    @Test
    void multipleFailedThenLockout_emitsCorrectSequence() throws Exception {
        createAccount("user3", Account.AccountStatus.ACTIVE);

        // 9 failed attempts
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                                    .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"user3\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // 10th triggers lockout
        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user3\",\"password\":\"wrong\"}"))
                .andExpect(status().is(423));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(10, logs.size());

        // First 9 are LOGIN_FAILED
        for (int i = 0; i < 9; i++) {
            assertEquals("LOGIN_FAILED", logs.get(i).getActionType());
        }
        // 10th is ACCOUNT_LOCKOUT
        assertEquals("ACCOUNT_LOCKOUT", logs.get(9).getActionType());
    }

    @Test
    void auditRecords_neverContainPasswords() throws Exception {
        createAccount("user4", Account.AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user4\",\"password\":\"supersecret123\"}"))
                .andExpect(status().isUnauthorized());

        List<AuditLog> logs = auditLogRepository.findAll();
        for (AuditLog log : logs) {
            String allFields = String.join("|",
                    nvl(log.getActionType()), nvl(log.getBeforeState()),
                    nvl(log.getAfterState()), nvl(log.getReasonCode()),
                    nvl(log.getObjectId()), nvl(log.getObjectType()));
            assertTrue(!allFields.contains("supersecret123"),
                    "Audit records must never contain passwords");
        }
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

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
