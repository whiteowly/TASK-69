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

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterIntegrationTest {

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
    void register_withValidData_creates201AndPersistsAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Jane.User\",\"password\":\"securepass1\",\"accountType\":\"PERSON\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("jane.user")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        Optional<Account> saved = accountRepository.findByUsername("jane.user");
        assertTrue(saved.isPresent());
        assertTrue(passwordEncoder.matches("securepass1", saved.get().getPasswordHash()));
        assertEquals(Account.AccountType.PERSON, saved.get().getAccountType());
    }

    @Test
    void register_normalizesCaseOnUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"CamelCase.User\",\"password\":\"securepass1\",\"accountType\":\"PERSON\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("camelcase.user")));
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"taken\",\"password\":\"securepass1\",\"accountType\":\"PERSON\"}"))
                .andExpect(status().isCreated());

        // Duplicate
        mockMvc.perform(post("/api/v1/auth/register")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"taken\",\"password\":\"securepass2\",\"accountType\":\"PERSON\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")));
    }

    @Test
    void register_invalidUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"securepass1\",\"accountType\":\"PERSON\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"validuser\",\"password\":\"short\",\"accountType\":\"PERSON\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_emitsAuditLog() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"audituser\",\"password\":\"securepass1\",\"accountType\":\"PERSON\"}"))
                .andExpect(status().isCreated());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertEquals(1, logs.size());
        assertEquals("ACCOUNT_REGISTERED", logs.get(0).getActionType());
        assertEquals("ACCOUNT", logs.get(0).getObjectType());
    }

    @Test
    void register_thenLoginSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"logintest\",\"password\":\"securepass1\",\"accountType\":\"PERSON\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                                .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"logintest\",\"password\":\"securepass1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("logintest")));
    }
}
