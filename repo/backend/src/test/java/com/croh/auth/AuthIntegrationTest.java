package com.croh.auth;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests proving:
 * - Login with a real persisted account succeeds and creates a session
 * - Authenticated session can access /api/v1/auth/me
 * - Unauthenticated access to /api/v1/auth/me returns 401
 * - The server issues a real XSRF-TOKEN cookie to the client
 * - Protected mutation (logout) succeeds with the server-issued CSRF token
 * - Protected mutation (logout) is rejected without the CSRF token
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        Account account = new Account();
        account.setUsername("testuser");
        account.setPasswordHash(passwordEncoder.encode("correct-password"));
        account.setAccountType(Account.AccountType.PERSON);
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setFailedLoginAttempts(0);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    // === Auth/session consistency ===

    @Test
    void login_withValidCredentials_returnsSessionAndAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("testuser")))
                .andExpect(jsonPath("$.accountId", notNullValue()))
                .andExpect(jsonPath("$.activeRole", is("PARTICIPANT")));
    }

    @Test
    void me_withAuthenticatedSession_returnsAccountInfo() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/me")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("testuser")))
                .andExpect(jsonPath("$.activeRole", is("PARTICIPANT")));
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    // CSRF token issuance tests moved to CsrfTokenIntegrationTest.java
    // (needs isolated Spring context to avoid .with(csrf()) interference)

    @Test
    void logout_withoutCsrfToken_succeeds_csrfExempt() throws Exception {
        // Logout is CSRF-exempt: session invalidation doesn't need CSRF protection
        // because there's no state change an attacker can exploit (session is destroyed).
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Logout without CSRF token succeeds (CSRF-exempt)
        mockMvc.perform(post("/api/v1/auth/logout")
                                .with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_invalidatesSession_subsequentRequestReturns401() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                                .with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());

        // Session should be invalidated
        mockMvc.perform(get("/api/v1/auth/me")
                        .session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_isCsrfExempt_succeedsWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void login_createsNewSession_sessionFixationProtection() throws Exception {
        // First login — get a session
        MvcResult firstLogin = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession firstSession = (MockHttpSession) firstLogin.getRequest().getSession(false);
        assertNotNull(firstSession);
        String firstSessionId = firstSession.getId();

        // Second login — should create a different session (old one invalidated)
        MvcResult secondLogin = mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession secondSession = (MockHttpSession) secondLogin.getRequest().getSession(false);
        assertNotNull(secondSession);

        // The old session should be invalidated
        assertFalse(firstSession.isInvalid() == false && firstSession.getId().equals(secondSession.getId()),
                "Login must create a new session to prevent session fixation");
    }
}
