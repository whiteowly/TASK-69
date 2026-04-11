package com.croh.resources;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PolicyListIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UsagePolicyRepository policyRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        policyRepository.deleteAll();
        accountRepository.deleteAll();
        admin = createAccount("admin");
    }

    @Test
    void listPolicies_returnsCreatedPolicies() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Create policy
        mockMvc.perform(post("/api/v1/resource-policies")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"name\":\"Household Limit\",\"scope\":\"HOUSEHOLD\",\"maxActions\":1,\"windowDays\":30,\"resourceAction\":\"CLAIM\"}"))
                .andExpect(status().isCreated());

        // List policies
        mockMvc.perform(get("/api/v1/resource-policies")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Household Limit")))
                .andExpect(jsonPath("$[0].scope", is("HOUSEHOLD")));
    }

    @Test
    void listPolicies_requiresPermission() throws Exception {
        Account participant = createAccount("participant");
        MockHttpSession session = sessionFor(participant, RoleType.PARTICIPANT);

        mockMvc.perform(get("/api/v1/resource-policies")
                        .session(session))
                .andExpect(status().isForbidden());
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
