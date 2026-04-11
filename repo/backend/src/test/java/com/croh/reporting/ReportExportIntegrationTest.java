package com.croh.reporting;

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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportExportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private MetricDefinitionRepository metricRepository;
    @Autowired private ReportTemplateRepository templateRepository;
    @Autowired private ReportExecutionRepository executionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        executionRepository.deleteAll();
        templateRepository.deleteAll();
        metricRepository.deleteAll();
        accountRepository.deleteAll();
        admin = createAccount("admin");
    }

    @Test
    void executeReport_csv_createsLocalFileArtifact() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        // Create template
        String templateBody = mockMvc.perform(post("/api/v1/reports/templates")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"name\":\"Monthly Summary\",\"description\":\"test\",\"metricIds\":\"1\",\"outputFormat\":\"CSV\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long templateId = Long.parseLong(templateBody.split("\"id\":")[1].split("[,}]")[0].trim());

        // Execute report as CSV
        mockMvc.perform(post("/api/v1/reports/templates/" + templateId + "/execute")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"format\":\"CSV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.exportFilePath", containsString(".csv")));

        // Verify audit
        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(l -> "REPORT_EXPORTED".equals(l.getActionType())));
    }

    @Test
    void executeReport_pdf_createsLocalFileArtifact() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        String templateBody = mockMvc.perform(post("/api/v1/reports/templates")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"name\":\"PDF Report\",\"metricIds\":\"1\",\"outputFormat\":\"PDF\"}"))
                .andReturn().getResponse().getContentAsString();
        Long templateId = Long.parseLong(templateBody.split("\"id\":")[1].split("[,}]")[0].trim());

        mockMvc.perform(post("/api/v1/reports/templates/" + templateId + "/execute")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"format\":\"PDF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.exportFilePath", containsString(".pdf")));
    }

    @Test
    void executeReport_requiresExportPermission() throws Exception {
        // Participant doesn't have EXPORT_REPORTS
        MockHttpSession session = sessionFor(createAccount("participant"), RoleType.PARTICIPANT);

        mockMvc.perform(post("/api/v1/reports/templates/999/execute")
                                .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"format\":\"CSV\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void analyticsOperationsSummary_returnsMetrics() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        mockMvc.perform(get("/api/v1/analytics/operations-summary")
                        .session(session)
                        .param("from", "2026-01-01T00:00:00")
                        .param("to", "2026-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistrations", notNullValue()))
                .andExpect(jsonPath("$.totalClaims", notNullValue()));
    }

    @Test
    void auditLogViewer_returnsPaginatedResults() throws Exception {
        MockHttpSession session = sessionFor(admin, RoleType.ADMIN);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .session(session)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)));
    }

    @Test
    void auditLogViewer_requiresViewAuditLogsPermission() throws Exception {
        MockHttpSession session = sessionFor(createAccount("p2"), RoleType.PARTICIPANT);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
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
