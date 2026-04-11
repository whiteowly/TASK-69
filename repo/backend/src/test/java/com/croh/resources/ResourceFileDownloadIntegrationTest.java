package com.croh.resources;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.croh.audit.AuditLogRepository;
import com.croh.files.FileStorageService;
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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceFileDownloadIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ResourceItemRepository resourceRepository;
    @Autowired private DownloadRecordRepository downloadRecordRepository;
    @Autowired private ClaimRecordRepository claimRecordRepository;
    @Autowired private PrintableNoticeRepository noticeRepository;
    @Autowired private UsagePolicyRepository policyRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private FileStorageService fileStorageService;

    private Account orgUser;
    private Account participant;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        noticeRepository.deleteAll();
        claimRecordRepository.deleteAll();
        downloadRecordRepository.deleteAll();
        resourceRepository.deleteAll();
        policyRepository.deleteAll();
        accountRepository.deleteAll();
        orgUser = createAccount("orguser");
        participant = createAccount("participant");
    }

    @Test
    void downloadResource_withRealFile_returnsActualFileBytes() throws Exception {
        // Store file through managed FileStorageService
        byte[] content = "PDF-test-content-bytes".getBytes();
        String storedPath = fileStorageService.store(content, "resources");

        ResourceItem r = new ResourceItem();
        r.setType("DOWNLOADABLE_FILE");
        r.setTitle("Storm Guide");
        r.setFileVersion("v1");
        r.setFilePath(storedPath);
        r.setFileSize((long) content.length);
        r.setContentType("application/pdf");
        r.setStatus("PUBLISHED");
        r.setCreatedBy(orgUser.getId());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        Long resId = resourceRepository.save(r).getId();

        MockHttpSession session = sessionFor(participant, RoleType.PARTICIPANT);

        // First: record a download (policy check)
        mockMvc.perform(post("/api/v1/resources/files/" + resId + "/download")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"fileVersion\":\"v1\"}"))
                .andExpect(jsonPath("$.result", is("ALLOWED")));

        // Then: fetch actual file bytes
        byte[] responseBytes = mockMvc.perform(get("/api/v1/resources/" + resId + "/file")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(content, responseBytes);
    }

    @Test
    void downloadResource_withoutAllowedRecord_returns403() throws Exception {
        byte[] content = "test".getBytes();
        String storedPath = fileStorageService.store(content, "resources");

        ResourceItem r = new ResourceItem();
        r.setType("DOWNLOADABLE_FILE");
        r.setTitle("Guide");
        r.setFileVersion("v1");
        r.setFilePath(storedPath);
        r.setFileSize(100L);
        r.setContentType("application/pdf");
        r.setStatus("PUBLISHED");
        r.setCreatedBy(orgUser.getId());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        Long resId = resourceRepository.save(r).getId();

        MockHttpSession session = sessionFor(participant, RoleType.PARTICIPANT);

        // No download record — should be denied
        mockMvc.perform(get("/api/v1/resources/" + resId + "/file")
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
