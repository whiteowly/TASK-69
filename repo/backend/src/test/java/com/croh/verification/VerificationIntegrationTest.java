package com.croh.verification;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.croh.audit.AuditLog;
import com.croh.audit.AuditLogRepository;
import com.croh.crypto.EncryptionService;
import com.croh.security.RolePermissions;
import com.croh.security.RoleType;
import com.croh.security.SessionAccount;
import com.croh.security.Permission;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
class VerificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PersonVerificationRepository personVerificationRepository;
    @Autowired private OrganizationCredentialDocumentRepository orgDocRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EncryptionService encryptionService;

    private Account user;
    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        orgDocRepository.deleteAll();
        personVerificationRepository.deleteAll();
        accountRepository.deleteAll();
        user = createAccount("testuser", Account.AccountType.ORGANIZATION);
        admin = createAccount("adminuser", Account.AccountType.PERSON);
    }

    // === Person verification ===

    @Test
    void submitPersonVerification_encryptsDobAtRest() throws Exception {
        mockMvc.perform(post("/api/v1/verification/person")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .content("{\"legalName\":\"Jane Doe\",\"dateOfBirth\":\"1992-01-20\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("UNDER_REVIEW")));

        PersonVerification pv = personVerificationRepository.findByAccountId(user.getId()).orElseThrow();
        assertNotEquals("1992-01-20", pv.getDobEncrypted());
        assertEquals("1992-01-20", encryptionService.decrypt(pv.getDobEncrypted()));
    }

    // === Upload with CSRF ===

    @Test
    void uploadOrgCredential_withCsrf_returns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", "content".getBytes());
        mockMvc.perform(multipart("/api/v1/verification/org-documents")
                        .file(file).session(sessionFor(user, RoleType.PARTICIPANT))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadOrgCredential_withoutCsrf_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", "content".getBytes());
        mockMvc.perform(multipart("/api/v1/verification/org-documents")
                        .file(file).session(sessionFor(user, RoleType.PARTICIPANT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadOrgCredential_invalidType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", "text".getBytes());
        mockMvc.perform(multipart("/api/v1/verification/org-documents")
                        .file(file).session(sessionFor(user, RoleType.PARTICIPANT))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadOrgCredential_duplicateChecksum_flaggedNotRejected() throws Exception {
        byte[] content = "identical content".getBytes();
        MockHttpSession session = sessionFor(user, RoleType.PARTICIPANT);

        mockMvc.perform(multipart("/api/v1/verification/org-documents")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", content))
                        .session(session)
                        .with(csrf()))
                .andExpect(jsonPath("$.duplicateChecksumFlag", is(false)));

        mockMvc.perform(multipart("/api/v1/verification/org-documents")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", content))
                        .session(session)
                        .with(csrf()))
                .andExpect(jsonPath("$.duplicateChecksumFlag", is(true)));

        assertEquals(2, orgDocRepository.findByAccountId(user.getId()).size());
    }

    // === PII access boundary — document download ===

    @Test
    void adminWithViewPii_canDownloadDocument_andAuditEmitted() throws Exception {
        byte[] originalContent = "real credential content".getBytes();
        uploadDoc(originalContent, "credential.pdf");
        OrganizationCredentialDocument doc = orgDocRepository.findByAccountId(user.getId()).get(0);

        // Admin has REVIEW_VERIFICATION + VIEW_PII (all permissions)
        MockHttpSession adminSession = sessionFor(admin, RoleType.ADMIN);
        auditLogRepository.deleteAll(); // clear prior audit

        MvcResult result = mockMvc.perform(get("/api/v1/admin/verification/org-document/" + doc.getId() + "/download")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();

        assertArrayEquals(originalContent, result.getResponse().getContentAsByteArray());

        // PII_VIEW audit emitted for document content access
        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(l -> "PII_VIEW".equals(l.getActionType())
                        && "DOCUMENT_CONTENT_ACCESS".equals(l.getReasonCode())));
    }

    @Test
    void reviewerWithoutViewPii_cannotDownloadDocument() throws Exception {
        uploadDoc("content".getBytes(), "cert.pdf");
        OrganizationCredentialDocument doc = orgDocRepository.findByAccountId(user.getId()).get(0);

        // VOLUNTEER has REVIEW_VERIFICATION but NOT VIEW_PII
        MockHttpSession volunteerSession = sessionFor(admin, RoleType.VOLUNTEER);

        mockMvc.perform(get("/api/v1/admin/verification/org-document/" + doc.getId() + "/download")
                        .session(volunteerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void participantWithoutReviewVerification_cannotDownloadDocument() throws Exception {
        uploadDoc("content".getBytes(), "cert.pdf");
        OrganizationCredentialDocument doc = orgDocRepository.findByAccountId(user.getId()).get(0);

        mockMvc.perform(get("/api/v1/admin/verification/org-document/" + doc.getId() + "/download")
                        .session(sessionFor(user, RoleType.PARTICIPANT)))
                .andExpect(status().isForbidden());
    }

    // === PII access boundary — DOB in queue ===

    @Test
    void adminQueue_withViewPii_showsRealDob_andAuditEmitted() throws Exception {
        submitPersonVerification();
        auditLogRepository.deleteAll();

        mockMvc.perform(get("/api/v1/admin/verification/queue")
                        .session(sessionFor(admin, RoleType.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dobMasked", is("1992-01-20")));

        // PII_VIEW audit for DOB access
        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(l -> "PII_VIEW".equals(l.getActionType())
                        && "DOB_ACCESS".equals(l.getReasonCode())));
    }

    @Test
    void adminQueue_withoutViewPii_showsMaskedDob_noAudit() throws Exception {
        submitPersonVerification();
        auditLogRepository.deleteAll();

        // VOLUNTEER has REVIEW_VERIFICATION but NOT VIEW_PII
        mockMvc.perform(get("/api/v1/admin/verification/queue")
                        .session(sessionFor(admin, RoleType.VOLUNTEER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dobMasked", is("****-**-**")));

        // No PII_VIEW audit emitted
        assertTrue(auditLogRepository.findAll().stream()
                .noneMatch(l -> "PII_VIEW".equals(l.getActionType())));
    }

    // === Admin decisions ===

    @Test
    void adminDecision_approvesPersonVerification() throws Exception {
        submitPersonVerification();
        PersonVerification pv = personVerificationRepository.findByAccountId(user.getId()).orElseThrow();

        mockMvc.perform(post("/api/v1/admin/verification/person/" + pv.getId() + "/decision")
                                .with(csrf())
                        .session(sessionFor(admin, RoleType.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .content("{\"decision\":\"APPROVE\",\"reasonCode\":\"DOC_VALID\",\"reviewNote\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    void adminQueue_requiresReviewVerificationPermission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/verification/queue")
                        .session(sessionFor(user, RoleType.PARTICIPANT)))
                .andExpect(status().isForbidden());
    }

    // === Helpers ===

    private void submitPersonVerification() throws Exception {
        mockMvc.perform(post("/api/v1/verification/person")
                                .with(csrf())
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .content("{\"legalName\":\"Jane Doe\",\"dateOfBirth\":\"1992-01-20\"}"))
                .andExpect(status().isAccepted());
    }

    private void uploadDoc(byte[] content, String name) throws Exception {
        mockMvc.perform(multipart("/api/v1/verification/org-documents")
                        .file(new MockMultipartFile("file", name, "application/pdf", content))
                        .session(sessionFor(user, RoleType.PARTICIPANT))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    private Account createAccount(String username) {
        return createAccount(username, Account.AccountType.PERSON);
    }

    private Account createAccount(String username, Account.AccountType type) {
        Account a = new Account();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode("password"));
        a.setAccountType(type);
        a.setStatus(Account.AccountStatus.ACTIVE);
        a.setFailedLoginAttempts(0);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        return accountRepository.save(a);
    }

    private MockHttpSession sessionFor(Account account, RoleType role) {
        SessionAccount sa = new SessionAccount(account.getId(), account.getUsername(),
                role, RolePermissions.getPermissions(role), "ACTIVE");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SESSION_ACCOUNT_KEY, sa);
        return session;
    }
}
