package com.croh.rewards;

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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RewardFulfillmentTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private RewardItemRepository rewardItemRepository;
    @Autowired private RewardOrderRepository rewardOrderRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account admin;
    private Account participant;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        rewardOrderRepository.deleteAll();
        rewardItemRepository.deleteAll();
        accountRepository.deleteAll();
        admin = createAccount("admin");
        participant = createAccount("participant");
    }

    @Test
    void createReward_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/rewards")
                                .with(csrf())
                        .session(sessionFor(admin, RoleType.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Water Kit\",\"inventoryCount\":100,\"perUserLimit\":2,\"fulfillmentType\":\"PHYSICAL_SHIPMENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Water Kit")));
    }

    @Test
    void placeOrder_decrementsInventory() throws Exception {
        Long rewardId = createReward(10, 2);

        mockMvc.perform(post("/api/v1/reward-orders")
                                .with(csrf())
                        .session(sessionFor(participant, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"rewardId\":" + rewardId + ",\"quantity\":1,\"fulfillmentType\":\"PHYSICAL_SHIPMENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ORDERED")));

        RewardItem item = rewardItemRepository.findById(rewardId).orElseThrow();
        assert item.getInventoryCount() == 9;
    }

    @Test
    void stateTransition_orderedToAllocated_succeeds() throws Exception {
        Long rewardId = createReward(10, 2);
        Long orderId = placeOrder(rewardId);

        mockMvc.perform(post("/api/v1/reward-orders/" + orderId + "/transition")
                                .with(csrf())
                        .session(sessionFor(admin, RoleType.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"toState\":\"ALLOCATED\",\"note\":\"Allocated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ALLOCATED")));
    }

    @Test
    void stateTransition_skipState_returns409() throws Exception {
        Long rewardId = createReward(10, 2);
        Long orderId = placeOrder(rewardId);

        // Try to skip from ORDERED to PACKED (illegal)
        mockMvc.perform(post("/api/v1/reward-orders/" + orderId + "/transition")
                                .with(csrf())
                        .session(sessionFor(admin, RoleType.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"toState\":\"PACKED\",\"note\":\"skip\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void perUserLimit_exceeded_returns409() throws Exception {
        Long rewardId = createReward(10, 1); // limit 1 per user
        placeOrder(rewardId);

        // Second order should fail
        mockMvc.perform(post("/api/v1/reward-orders")
                                .with(csrf())
                        .session(sessionFor(participant, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"rewardId\":" + rewardId + ",\"quantity\":1,\"fulfillmentType\":\"PHYSICAL_SHIPMENT\"}"))
                .andExpect(status().isConflict());
    }

    private Long createReward(int inventory, int perUserLimit) throws Exception {
        String body = mockMvc.perform(post("/api/v1/rewards")
                                .with(csrf())
                        .session(sessionFor(admin, RoleType.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Reward\",\"inventoryCount\":" + inventory + ",\"perUserLimit\":" + perUserLimit + ",\"fulfillmentType\":\"PHYSICAL_SHIPMENT\"}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.split("\"id\":")[1].split("[,}]")[0].trim());
    }

    private Long placeOrder(Long rewardId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/reward-orders")
                                .with(csrf())
                        .session(sessionFor(participant, RoleType.PARTICIPANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"rewardId\":" + rewardId + ",\"quantity\":1,\"fulfillmentType\":\"PHYSICAL_SHIPMENT\"}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.split("\"id\":")[1].split("[,}]")[0].trim());
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
