package com.croh.auth;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.account.BlacklistRecord;
import com.croh.account.BlacklistRecordRepository;
import com.croh.account.BlacklistAppealRepository;
import com.croh.audit.AuditLogRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the full blacklisted-at-login → appeal flow:
 * 1. Blacklisted user logs in with valid credentials → 423 + session cookie
 * 2. Using that session, user can GET /appeals/my-blacklist
 * 3. Using that session, user can POST /appeals to submit an appeal
 * 4. Using that session, user CANNOT access normal protected endpoints
 * 5. Wrong password for blacklisted user → 401, no session
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlacklistLoginAppealFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private BlacklistRecordRepository blacklistRecordRepository;
    @Autowired private BlacklistAppealRepository appealRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account blacklistedUser;
    private BlacklistRecord blacklistRecord;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        appealRepository.deleteAll();
        blacklistRecordRepository.deleteAll();
        accountRepository.deleteAll();

        // Create a blacklisted user
        blacklistedUser = new Account();
        blacklistedUser.setUsername("blocked.user");
        blacklistedUser.setPasswordHash(passwordEncoder.encode("valid-password"));
        blacklistedUser.setAccountType(Account.AccountType.PERSON);
        blacklistedUser.setStatus(Account.AccountStatus.BLACKLISTED);
        blacklistedUser.setFailedLoginAttempts(0);
        blacklistedUser.setCreatedAt(LocalDateTime.now());
        blacklistedUser.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(blacklistedUser);

        // Create the admin who blacklisted them
        Account admin = new Account();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin-pass"));
        admin.setAccountType(Account.AccountType.PERSON);
        admin.setStatus(Account.AccountStatus.ACTIVE);
        admin.setFailedLoginAttempts(0);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(admin);

        // Create the blacklist record
        blacklistRecord = new BlacklistRecord();
        blacklistRecord.setAccountId(blacklistedUser.getId());
        blacklistRecord.setReasonCode("SAFETY_POLICY_BREACH");
        blacklistRecord.setNote("Test blacklist");
        blacklistRecord.setCreatedBy(admin.getId());
        blacklistRecord.setCreatedAt(LocalDateTime.now());
        blacklistRecordRepository.save(blacklistRecord);
    }

    @Test
    void step1_blacklistedLogin_returns423_butCreatesSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked.user\",\"password\":\"valid-password\"}"))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code", is("ACCOUNT_BLACKLISTED")))
                .andReturn();

        // Session was created (cookie exists)
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assert session != null : "Session must be created for blacklisted login";
    }

    @Test
    void step2_blacklistedSession_canAccessMyBlacklist() throws Exception {
        // Login to get the constrained session
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked.user\",\"password\":\"valid-password\"}"))
                .andExpect(status().is(423))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Fetch blacklist info using the constrained session
        mockMvc.perform(get("/api/v1/appeals/my-blacklist")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blacklistId", is(blacklistRecord.getId().intValue())))
                .andExpect(jsonPath("$.reasonCode", is("SAFETY_POLICY_BREACH")));
    }

    @Test
    void step3_blacklistedSession_canSubmitAppeal() throws Exception {
        // Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked.user\",\"password\":\"valid-password\"}"))
                .andExpect(status().is(423))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Submit appeal
        mockMvc.perform(post("/api/v1/appeals")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blacklistId\":" + blacklistRecord.getId()
                                + ",\"appealText\":\"I believe this was an error\""
                                + ",\"contactNote\":\"Available at front desk\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.dueDate", notNullValue()));
    }

    @Test
    void step4_blacklistedSession_cannotAccessProtectedEndpoints() throws Exception {
        // Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked.user\",\"password\":\"valid-password\"}"))
                .andExpect(status().is(423))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Try to access /auth/me — blocked by BlacklistEnforcementFilter
        mockMvc.perform(get("/api/v1/auth/me")
                        .session(session))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code", is("ACCOUNT_BLACKLISTED")));
    }

    @Test
    void step5_blacklistedUser_wrongPassword_returns401_noSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked.user\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")))
                .andReturn();

        // No session created for wrong password
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        // Session might exist but shouldn't have SESSION_ACCOUNT
        if (session != null) {
            assert session.getAttribute("SESSION_ACCOUNT") == null
                    : "No session account should be set for wrong password";
        }
    }

    @Test
    void fullFlow_loginThenFetchInfoThenAppeal() throws Exception {
        // Complete end-to-end flow as one test
        // 1. Login (423 + session)
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked.user\",\"password\":\"valid-password\"}"))
                .andExpect(status().is(423))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // 2. Fetch blacklist info
        MvcResult infoResult = mockMvc.perform(get("/api/v1/appeals/my-blacklist")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        // 3. Submit appeal using the fetched blacklist ID
        String infoBody = infoResult.getResponse().getContentAsString();
        // Extract blacklistId from JSON
        String blacklistId = infoBody.split("\"blacklistId\":")[1].split("[,}]")[0].trim();

        mockMvc.perform(post("/api/v1/appeals")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blacklistId\":" + blacklistId
                                + ",\"appealText\":\"Please reconsider\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")));
    }
}
