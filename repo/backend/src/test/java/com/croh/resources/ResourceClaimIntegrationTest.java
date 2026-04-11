package com.croh.resources;

import com.croh.account.Account;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.croh.account.AccountRepository;
import com.croh.audit.AuditLogRepository;
import com.croh.rewards.ShippingAddress;
import com.croh.rewards.ShippingAddressRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceClaimIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UsagePolicyRepository policyRepository;
    @Autowired private ResourceItemRepository resourceRepository;
    @Autowired private ClaimRecordRepository claimRecordRepository;
    @Autowired private DownloadRecordRepository downloadRecordRepository;
    @Autowired private PrintableNoticeRepository noticeRepository;
    @Autowired private ShippingAddressRepository addressRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account user1;
    private Account user2;
    private Account orgUser;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        noticeRepository.deleteAll();
        claimRecordRepository.deleteAll();
        downloadRecordRepository.deleteAll();
        resourceRepository.deleteAll();
        policyRepository.deleteAll();
        addressRepository.deleteAll();
        accountRepository.deleteAll();
        user1 = createAccount("user1");
        user2 = createAccount("user2");
        orgUser = createAccount("orguser");
    }

    @Test
    void claimResource_allowed_decrementsInventory() throws Exception {
        Long resId = createResource("CLAIMABLE_ITEM", 10, null);

        mockMvc.perform(post("/api/v1/resources/" + resId + "/claim")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", is("ALLOWED")));

        ResourceItem item = resourceRepository.findById(resId).orElseThrow();
        assert item.getInventoryCount() == 9;
    }

    @Test
    void claimResource_perUserLimit_deniedAfterMax() throws Exception {
        Long policyId = createPolicy("USER", 1, 30, "CLAIM");
        Long resId = createResource("CLAIMABLE_ITEM", 10, policyId);

        // First claim succeeds
        mockMvc.perform(post("/api/v1/resources/" + resId + "/claim")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(jsonPath("$.result", is("ALLOWED")));

        // Second claim denied
        mockMvc.perform(post("/api/v1/resources/" + resId + "/claim")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(jsonPath("$.result", is("DENIED_POLICY")))
                .andExpect(jsonPath("$.reasonCode", is("POLICY_LIMIT_EXCEEDED")));
    }

    @Test
    void claimResource_householdLimit_derivedFromPrimaryAddress() throws Exception {
        // Both users share the same household (same primary address)
        createPrimaryAddress(user1.getId(), "Springfield", "IL", "62701");
        createPrimaryAddress(user2.getId(), "Springfield", "IL", "62701");

        Long policyId = createPolicy("HOUSEHOLD", 1, 30, "CLAIM");
        Long resId = createResource("CLAIMABLE_ITEM", 10, policyId);

        // user1 claims
        mockMvc.perform(post("/api/v1/resources/" + resId + "/claim")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(jsonPath("$.result", is("ALLOWED")));

        // user2 same household — denied
        mockMvc.perform(post("/api/v1/resources/" + resId + "/claim")
                                .with(csrf())
                        .session(sessionFor(user2, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(jsonPath("$.result", is("DENIED_POLICY")));
    }

    @Test
    void claimResource_householdLimit_differentAddresses_bothAllowed() throws Exception {
        createPrimaryAddress(user1.getId(), "Springfield", "IL", "62701");
        createPrimaryAddress(user2.getId(), "Chicago", "IL", "60601");

        Long policyId = createPolicy("HOUSEHOLD", 1, 30, "CLAIM");
        Long resId = createResource("CLAIMABLE_ITEM", 10, policyId);

        mockMvc.perform(post("/api/v1/resources/" + resId + "/claim")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(jsonPath("$.result", is("ALLOWED")));

        mockMvc.perform(post("/api/v1/resources/" + resId + "/claim")
                                .with(csrf())
                        .session(sessionFor(user2, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(jsonPath("$.result", is("ALLOWED")));
    }

    @Test
    void claimResource_householdLimit_noAddress_returns409() throws Exception {
        Long policyId = createPolicy("HOUSEHOLD", 1, 30, "CLAIM");
        Long resId = createResource("CLAIMABLE_ITEM", 10, policyId);

        mockMvc.perform(post("/api/v1/resources/" + resId + "/claim")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isConflict());
    }

    @Test
    void downloadResource_perUserLimit_enforced() throws Exception {
        Long policyId = createPolicy("USER", 2, 30, "DOWNLOAD");
        Long resId = createResource("DOWNLOADABLE_FILE", null, policyId);

        mockMvc.perform(post("/api/v1/resources/files/" + resId + "/download")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"fileVersion\":\"v1\"}"))
                .andExpect(jsonPath("$.result", is("ALLOWED")));

        mockMvc.perform(post("/api/v1/resources/files/" + resId + "/download")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"fileVersion\":\"v1\"}"))
                .andExpect(jsonPath("$.result", is("ALLOWED")));

        // Third download exceeds limit
        mockMvc.perform(post("/api/v1/resources/files/" + resId + "/download")
                                .with(csrf())
                        .session(sessionFor(user1, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"fileVersion\":\"v1\"}"))
                .andExpect(jsonPath("$.result", is("DENIED_POLICY")));
    }

    private Long createPolicy(String scope, int maxActions, int windowDays, String action) {
        UsagePolicy p = new UsagePolicy();
        p.setName("test-" + scope);
        p.setScope(scope);
        p.setMaxActions(maxActions);
        p.setWindowDays(windowDays);
        p.setResourceAction(action);
        p.setCreatedAt(LocalDateTime.now());
        return policyRepository.save(p).getId();
    }

    private Long createResource(String type, Integer inventory, Long policyId) {
        ResourceItem r = new ResourceItem();
        r.setType(type);
        r.setTitle("Test Resource");
        r.setInventoryCount(inventory);
        r.setUsagePolicyId(policyId);
        r.setStatus("PUBLISHED");
        r.setCreatedBy(orgUser.getId());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return resourceRepository.save(r).getId();
    }

    private void createPrimaryAddress(Long accountId, String city, String state, String zip) {
        ShippingAddress addr = new ShippingAddress();
        addr.setAccountId(accountId);
        addr.setAddressLine1Encrypted("encrypted");
        addr.setCity(city);
        addr.setStateCode(state);
        addr.setZipCode(zip);
        addr.setPrimary(true);
        addr.setCreatedAt(LocalDateTime.now());
        addressRepository.save(addr);
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
