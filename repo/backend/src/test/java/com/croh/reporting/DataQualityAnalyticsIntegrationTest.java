package com.croh.reporting;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.croh.audit.AuditLogRepository;
import com.croh.security.RolePermissions;
import com.croh.security.RoleType;
import com.croh.security.SessionAccount;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataQualityAnalyticsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
        admin = createAccount("admin");
    }

    @Test
    void dataQuality_duplicateMetric_returnsCount() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Issue 5: data-quality endpoint should query correct table/field
        mockMvc.perform(get("/api/v1/reports/data-quality")
                        .session(session)
                        .param("from", "2020-01-01T00:00:00")
                        .param("to", "2030-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential_duplicates", notNullValue()));
    }

    @Test
    void analyticsOrgFilter_filteredVsUnfiltered() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Issue 8: unfiltered request
        mockMvc.perform(get("/api/v1/analytics/operations-summary")
                        .session(session)
                        .param("from", "2020-01-01T00:00:00")
                        .param("to", "2030-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistrations", notNullValue()));

        // Issue 8: filtered by orgId
        mockMvc.perform(get("/api/v1/analytics/operations-summary")
                        .session(session)
                        .param("from", "2020-01-01T00:00:00")
                        .param("to", "2030-12-31T23:59:59")
                        .param("orgId", "org_nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistrations", is(0)));
    }

    @Test
    void reportExecution_appliesFiltersToMetricQueries() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Create a metric definition with a query that uses :from and :to parameters
        String metricBody = mockMvc.perform(post("/api/v1/reports/metric-definitions")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"name\":\"Account Count\",\"description\":\"test\",\"queryTemplate\":\"SELECT COUNT(*) FROM account WHERE created_at BETWEEN :from AND :to\",\"domain\":\"accounts\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String metricId = metricBody.split("\"id\":")[1].split("[,}]")[0].trim();

        // Create template referencing the metric
        String templateBody = mockMvc.perform(post("/api/v1/reports/templates")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"name\":\"Filtered Report\",\"metricIds\":\"" + metricId + "\",\"outputFormat\":\"CSV\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String templateId = templateBody.split("\"id\":")[1].split("[,}]")[0].trim();

        // Execute with validated filters — should apply :from/:to bindings
        mockMvc.perform(post("/api/v1/reports/templates/" + templateId + "/execute")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"format\":\"CSV\",\"filters\":\"{\\\"from\\\":\\\"2020-01-01T00:00:00\\\",\\\"to\\\":\\\"2030-12-31T23:59:59\\\"}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.exportFilePath", containsString(".csv")));
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
