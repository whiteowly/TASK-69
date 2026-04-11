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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddressPrimaryIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ShippingAddressRepository addressRepository;
    @Autowired private RewardItemRepository rewardRepository;
    @Autowired private RewardOrderRepository orderRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account participant;
    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        orderRepository.deleteAll();
        rewardRepository.deleteAll();
        addressRepository.deleteAll();
        accountRepository.deleteAll();
        participant = createAccount("participant1");
        admin = createAccount("admin1");
    }

    @Test
    void firstAddress_becomesPrimary() throws Exception {
        MockHttpSession session = sessionFor(participant, RoleType.PARTICIPANT);

        mockMvc.perform(post("/api/v1/accounts/me/addresses")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"line1\":\"123 Main St\",\"city\":\"Springfield\",\"state\":\"IL\",\"zip\":\"62701\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary", is(true)));
    }

    @Test
    void secondAddress_notPrimary() throws Exception {
        MockHttpSession session = sessionFor(participant, RoleType.PARTICIPANT);

        mockMvc.perform(post("/api/v1/accounts/me/addresses")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"line1\":\"123 Main St\",\"city\":\"Springfield\",\"state\":\"IL\",\"zip\":\"62701\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary", is(true)));

        mockMvc.perform(post("/api/v1/accounts/me/addresses")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"line1\":\"456 Oak Ave\",\"city\":\"Chicago\",\"state\":\"IL\",\"zip\":\"60601\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary", is(false)));
    }

    @Test
    void setPrimary_switchesPrimaryAddress() throws Exception {
        MockHttpSession session = sessionFor(participant, RoleType.PARTICIPANT);

        // Create first address (auto-primary)
        String body1 = mockMvc.perform(post("/api/v1/accounts/me/addresses")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"line1\":\"123 Main St\",\"city\":\"Springfield\",\"state\":\"IL\",\"zip\":\"62701\"}"))
                .andReturn().getResponse().getContentAsString();

        // Create second address
        String body2 = mockMvc.perform(post("/api/v1/accounts/me/addresses")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"line1\":\"456 Oak Ave\",\"city\":\"Chicago\",\"state\":\"IL\",\"zip\":\"60601\"}"))
                .andReturn().getResponse().getContentAsString();
        Long addr2Id = Long.parseLong(body2.split("\"id\":")[1].split("[,}]")[0].trim());

        // Set second as primary
        mockMvc.perform(put("/api/v1/accounts/me/addresses/" + addr2Id + "/primary")
                        .with(csrf())
                        .session(session)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary", is(true)));

        // Verify only one primary
        long primaryCount = addressRepository.findByAccountId(participant.getId()).stream()
                .filter(ShippingAddress::isPrimary)
                .count();
        assert primaryCount == 1;
    }

    @Test
    void listAddresses_returnsAll() throws Exception {
        MockHttpSession session = sessionFor(participant, RoleType.PARTICIPANT);

        mockMvc.perform(post("/api/v1/accounts/me/addresses")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"line1\":\"123 Main St\",\"city\":\"Springfield\",\"state\":\"IL\",\"zip\":\"62701\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/accounts/me/addresses")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void createAddress_thenPlaceShippingOrder_succeeds() throws Exception {
        MockHttpSession pSession = sessionFor(participant, RoleType.PARTICIPANT);
        MockHttpSession aSession = sessionFor(admin, RoleType.ADMIN);

        // Create address (becomes auto-primary)
        String addrBody = mockMvc.perform(post("/api/v1/accounts/me/addresses")
                        .with(csrf())
                        .session(pSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"line1\":\"123 Main St\",\"city\":\"Springfield\",\"state\":\"IL\",\"zip\":\"62701\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long addressId = Long.parseLong(addrBody.split("\"id\":")[1].split("[,}]")[0].trim());

        // Create reward
        String rewardBody = mockMvc.perform(post("/api/v1/rewards")
                        .with(csrf())
                        .session(aSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"title\":\"Kit\",\"inventoryCount\":10,\"perUserLimit\":2,\"fulfillmentType\":\"PHYSICAL_SHIPMENT\"}"))
                .andReturn().getResponse().getContentAsString();
        Long rewardId = Long.parseLong(rewardBody.split("\"id\":")[1].split("[,}]")[0].trim());

        // Place order with addressId
        mockMvc.perform(post("/api/v1/reward-orders")
                        .with(csrf())
                        .session(pSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                        .content("{\"rewardId\":" + rewardId + ",\"quantity\":1,\"fulfillmentType\":\"PHYSICAL_SHIPMENT\",\"addressId\":" + addressId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("ORDERED")));
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
