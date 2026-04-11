package com.croh.security;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
import com.croh.alerts.WorkOrder;
import com.croh.alerts.WorkOrderRepository;
import com.croh.alerts.WorkOrderNoteRepository;
import com.croh.alerts.WorkOrderPhotoRepository;
import com.croh.alerts.PostIncidentReviewRepository;
import com.croh.audit.AuditLogRepository;
import com.croh.rewards.RewardItem;
import com.croh.rewards.RewardItemRepository;
import com.croh.rewards.RewardOrder;
import com.croh.rewards.RewardOrderRepository;
import com.croh.rewards.FulfillmentExceptionRepository;
import com.croh.rewards.ShippingAddressRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

import java.time.LocalDateTime;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests proving object-centric org-scope authorization for reward-order
 * and work-order privileged actions.
 *
 * Authorization is checked against the object's own organizationId field,
 * NOT inferred from the creator's current role memberships.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrgScopeAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private RoleMembershipRepository roleMembershipRepository;
    @Autowired private RewardItemRepository rewardItemRepository;
    @Autowired private RewardOrderRepository rewardOrderRepository;
    @Autowired private FulfillmentExceptionRepository exceptionRepository;
    @Autowired private ShippingAddressRepository addressRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private WorkOrderNoteRepository noteRepository;
    @Autowired private WorkOrderPhotoRepository photoRepository;
    @Autowired private PostIncidentReviewRepository pirRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Account orgA_user;   // ORG_OPERATOR for org_A only
    private Account orgB_user;   // ORG_OPERATOR for org_B only
    private Account multiUser;   // ORG_OPERATOR for org_A AND org_B
    private Account admin;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        pirRepository.deleteAll();
        photoRepository.deleteAll();
        noteRepository.deleteAll();
        workOrderRepository.deleteAll();
        exceptionRepository.deleteAll();
        rewardOrderRepository.deleteAll();
        rewardItemRepository.deleteAll();
        addressRepository.deleteAll();
        roleMembershipRepository.deleteAll();
        accountRepository.deleteAll();

        orgA_user = createAccount("orgA_user");
        orgB_user = createAccount("orgB_user");
        multiUser = createAccount("multi_user");
        admin = createAccount("admin_user");

        createRoleMembership(orgA_user.getId(), "ORG_OPERATOR", "org_A");
        createRoleMembership(orgB_user.getId(), "ORG_OPERATOR", "org_B");
        createRoleMembership(multiUser.getId(), "ORG_OPERATOR", "org_A");
        createRoleMembership(multiUser.getId(), "ORG_OPERATOR", "org_B");
    }

    // ====================================================================
    //  Reward order scope tests (object-centric via reward_item.organization_id)
    // ====================================================================

    @Nested
    class RewardOrderScopeTests {

        private RewardOrder orgA_order;

        @BeforeEach
        void setUpOrders() {
            RewardItem rewardA = createReward(orgA_user.getId(), "org_A");
            orgA_order = createOrder(rewardA.getId());
        }

        @Test
        void transition_byOrgA_succeeds() throws Exception {
            mockMvc.perform(post("/api/v1/reward-orders/" + orgA_order.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toState\":\"ALLOCATED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ALLOCATED")));
        }

        @Test
        void transition_byOrgB_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/reward-orders/" + orgA_order.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(orgB_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toState\":\"ALLOCATED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void transition_byAdmin_succeeds() throws Exception {
            mockMvc.perform(post("/api/v1/reward-orders/" + orgA_order.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(admin, RoleType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toState\":\"ALLOCATED\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void tracking_byOrgB_returns403() throws Exception {
            advanceOrderTo(orgA_order, "SHIPPED");

            mockMvc.perform(post("/api/v1/reward-orders/" + orgA_order.getId() + "/tracking")
                            .with(csrf())
                            .session(sessionFor(orgB_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"trackingNumber\":\"TRK123\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void tracking_byOrgA_succeeds() throws Exception {
            advanceOrderTo(orgA_order, "SHIPPED");

            mockMvc.perform(post("/api/v1/reward-orders/" + orgA_order.getId() + "/tracking")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"trackingNumber\":\"TRK123\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void voucher_byOrgB_returns403() throws Exception {
            RewardItem rewardA = createReward(orgA_user.getId(), "org_A", "VOUCHER");
            RewardOrder vOrder = createOrder(rewardA.getId(), "VOUCHER");
            advanceVoucherOrderTo(vOrder, "VOUCHER_ISSUED");

            mockMvc.perform(post("/api/v1/reward-orders/" + vOrder.getId() + "/voucher")
                            .with(csrf())
                            .session(sessionFor(orgB_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"voucherCode\":\"VOUCH001\"}"))
                    .andExpect(status().isForbidden());
        }

        private void advanceOrderTo(RewardOrder order, String targetStatus) {
            String[] chain = {"ALLOCATED", "PACKED", "SHIPPED"};
            for (String s : chain) {
                order.setStatus(s);
                order.setStatusChangedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                rewardOrderRepository.save(order);
                if (s.equals(targetStatus)) break;
            }
        }

        private void advanceVoucherOrderTo(RewardOrder order, String targetStatus) {
            String[] chain = {"ALLOCATED", "VOUCHER_ISSUED"};
            for (String s : chain) {
                order.setStatus(s);
                order.setStatusChangedAt(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                rewardOrderRepository.save(order);
                if (s.equals(targetStatus)) break;
            }
        }
    }

    // ====================================================================
    //  Work order scope tests (object-centric via work_order.organization_id)
    // ====================================================================

    @Nested
    class WorkOrderScopeTests {

        private WorkOrder orgA_wo;
        private WorkOrder orgB_wo;

        @BeforeEach
        void setUpWorkOrders() {
            orgA_wo = createWorkOrder(orgA_user.getId(), "org_A");
            orgB_wo = createWorkOrder(orgB_user.getId(), "org_B");
        }

        @Test
        void getWorkOrder_byOrgA_onOrgA_succeeds() throws Exception {
            mockMvc.perform(get("/api/v1/work-orders/" + orgA_wo.getId())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(orgA_wo.getId().intValue())));
        }

        @Test
        void getWorkOrder_byOrgA_onOrgB_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/work-orders/" + orgB_wo.getId())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void getWorkOrder_byAdmin_onAnyOrg_succeeds() throws Exception {
            mockMvc.perform(get("/api/v1/work-orders/" + orgB_wo.getId())
                            .session(sessionFor(admin, RoleType.ADMIN)))
                    .andExpect(status().isOk());
        }

        @Test
        void transition_byOrgA_onOrgB_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders/" + orgB_wo.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toStatus\":\"ACKNOWLEDGED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void transition_byOrgA_onOrgA_succeeds() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders/" + orgA_wo.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toStatus\":\"ACKNOWLEDGED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ACKNOWLEDGED")));
        }

        @Test
        void assign_byOrgA_onOrgB_returns403() throws Exception {
            orgB_wo.setStatus("DISPATCHED");
            orgB_wo.setUpdatedAt(LocalDateTime.now());
            workOrderRepository.save(orgB_wo);

            mockMvc.perform(post("/api/v1/work-orders/" + orgB_wo.getId() + "/assign")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"assigneeId\":" + orgA_user.getId() + "}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void addNote_byOrgA_onOrgB_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders/" + orgB_wo.getId() + "/notes")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"content\":\"cross-org note\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void addNote_byOrgA_onOrgA_succeeds() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders/" + orgA_wo.getId() + "/notes")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"content\":\"valid note\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        void addPhoto_byOrgA_onOrgB_returns403() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", "fakeimg".getBytes());

            mockMvc.perform(multipart("/api/v1/work-orders/" + orgB_wo.getId() + "/photos")
                            .file(file)
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void postIncidentReview_byOrgA_onOrgB_returns403() throws Exception {
            orgB_wo.setStatus("CLOSED");
            orgB_wo.setClosedAt(LocalDateTime.now());
            orgB_wo.setUpdatedAt(LocalDateTime.now());
            workOrderRepository.save(orgB_wo);

            mockMvc.perform(post("/api/v1/work-orders/" + orgB_wo.getId() + "/post-incident-review")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"summary\":\"cross-org review\",\"lessons\":\"\",\"actions\":\"\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void admin_canActOnAnyWorkOrder() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders/" + orgB_wo.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(admin, RoleType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toStatus\":\"ACKNOWLEDGED\"}"))
                    .andExpect(status().isOk());
        }
    }

    // ====================================================================
    //  Multi-membership ambiguity regression tests
    //  Proves authorization follows the object's organizationId,
    //  NOT the creator's current membership set.
    // ====================================================================

    @Nested
    class MultiMembershipAmbiguityTests {

        @Test
        void multiMemberCreator_objectBoundToOrgA_orgBOnly_cannotAccess() throws Exception {
            // multiUser has memberships in BOTH org_A and org_B.
            // Create a reward explicitly bound to org_A.
            RewardItem reward = createReward(multiUser.getId(), "org_A");
            RewardOrder order = createOrder(reward.getId());

            // orgB_user (org_B only) must NOT be able to access,
            // even though the creator (multiUser) also has org_B membership.
            mockMvc.perform(post("/api/v1/reward-orders/" + order.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(orgB_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toState\":\"ALLOCATED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void multiMemberCreator_objectBoundToOrgA_orgAOnly_canAccess() throws Exception {
            // Same setup — orgA_user (org_A only) CAN access because
            // the reward is bound to org_A.
            RewardItem reward = createReward(multiUser.getId(), "org_A");
            RewardOrder order = createOrder(reward.getId());

            mockMvc.perform(post("/api/v1/reward-orders/" + order.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toState\":\"ALLOCATED\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void multiMemberCreator_workOrderBoundToOrgB_orgAOnly_cannotAccess() throws Exception {
            // Work order explicitly bound to org_B, created by multiUser.
            WorkOrder wo = createWorkOrder(multiUser.getId(), "org_B");

            // orgA_user (org_A only) must NOT be able to access.
            mockMvc.perform(get("/api/v1/work-orders/" + wo.getId())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void multiMemberCreator_workOrderBoundToOrgB_orgBOnly_canAccess() throws Exception {
            WorkOrder wo = createWorkOrder(multiUser.getId(), "org_B");

            mockMvc.perform(get("/api/v1/work-orders/" + wo.getId())
                            .session(sessionFor(orgB_user, RoleType.ORG_OPERATOR)))
                    .andExpect(status().isOk());
        }

        @Test
        void legacyObject_nullOrgId_nonAdmin_returns403() throws Exception {
            // Simulate legacy data with no organizationId set
            RewardItem reward = new RewardItem();
            reward.setTitle("Legacy Reward");
            reward.setInventoryCount(10);
            reward.setPerUserLimit(5);
            reward.setFulfillmentType("PHYSICAL_SHIPMENT");
            reward.setStatus("ACTIVE");
            reward.setOrganizationId(null); // no org binding
            reward.setCreatedBy(orgA_user.getId());
            reward.setCreatedAt(LocalDateTime.now());
            reward.setUpdatedAt(LocalDateTime.now());
            rewardItemRepository.save(reward);

            RewardOrder order = createOrder(reward.getId());

            // orgA_user must be rejected (fail-safe for null org)
            mockMvc.perform(post("/api/v1/reward-orders/" + order.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toState\":\"ALLOCATED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void legacyObject_nullOrgId_admin_canAccess() throws Exception {
            RewardItem reward = new RewardItem();
            reward.setTitle("Legacy Reward");
            reward.setInventoryCount(10);
            reward.setPerUserLimit(5);
            reward.setFulfillmentType("PHYSICAL_SHIPMENT");
            reward.setStatus("ACTIVE");
            reward.setOrganizationId(null);
            reward.setCreatedBy(orgA_user.getId());
            reward.setCreatedAt(LocalDateTime.now());
            reward.setUpdatedAt(LocalDateTime.now());
            rewardItemRepository.save(reward);

            RewardOrder order = createOrder(reward.getId());

            mockMvc.perform(post("/api/v1/reward-orders/" + order.getId() + "/transition")
                            .with(csrf())
                            .session(sessionFor(admin, RoleType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"toState\":\"ALLOCATED\"}"))
                    .andExpect(status().isOk());
        }
    }

    // ====================================================================
    //  Creation-time org-scope enforcement tests
    // ====================================================================

    @Nested
    class CreateScopeTests {

        // --- Reward create ---

        @Test
        void createReward_orgA_forOrgA_succeeds() throws Exception {
            mockMvc.perform(post("/api/v1/rewards")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"title\":\"Kit\",\"inventoryCount\":5,\"organizationId\":\"org_A\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        void createReward_orgA_forOrgB_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/rewards")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"title\":\"Kit\",\"inventoryCount\":5,\"organizationId\":\"org_B\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void createReward_admin_forOrgB_succeeds() throws Exception {
            mockMvc.perform(post("/api/v1/rewards")
                            .with(csrf())
                            .session(sessionFor(admin, RoleType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"title\":\"Kit\",\"inventoryCount\":5,\"organizationId\":\"org_B\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        void createReward_orgA_nullOrg_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/rewards")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"title\":\"Kit\",\"inventoryCount\":5}"))
                    .andExpect(status().isForbidden());
        }

        // --- Work-order create ---

        @Test
        void createWorkOrder_orgA_forOrgA_succeeds() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"title\":\"Fix leak\",\"severity\":\"HIGH\",\"organizationId\":\"org_A\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        void createWorkOrder_orgA_forOrgB_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"title\":\"Fix leak\",\"severity\":\"HIGH\",\"organizationId\":\"org_B\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void createWorkOrder_admin_forOrgB_succeeds() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders")
                            .with(csrf())
                            .session(sessionFor(admin, RoleType.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"title\":\"Fix leak\",\"severity\":\"HIGH\",\"organizationId\":\"org_B\"}"))
                    .andExpect(status().isCreated());
        }

        @Test
        void createWorkOrder_orgA_nullOrg_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/work-orders")
                            .with(csrf())
                            .session(sessionFor(orgA_user, RoleType.ORG_OPERATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-XSRF-TOKEN", "t").cookie(new Cookie("XSRF-TOKEN", "t"))
                            .content("{\"title\":\"Fix leak\",\"severity\":\"HIGH\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ====================================================================
    //  Helpers
    // ====================================================================

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

    private void createRoleMembership(Long accountId, String roleType, String scopeId) {
        RoleMembership rm = new RoleMembership();
        rm.setAccountId(accountId);
        rm.setRoleType(roleType);
        rm.setScopeId(scopeId);
        rm.setStatus(RoleMembership.RoleMembershipStatus.APPROVED);
        rm.setCreatedAt(LocalDateTime.now());
        rm.setUpdatedAt(LocalDateTime.now());
        roleMembershipRepository.save(rm);
    }

    private RewardItem createReward(Long createdBy, String orgId) {
        return createReward(createdBy, orgId, "PHYSICAL_SHIPMENT");
    }

    private RewardItem createReward(Long createdBy, String orgId, String fulfillmentType) {
        RewardItem item = new RewardItem();
        item.setTitle("Test Reward");
        item.setInventoryCount(100);
        item.setPerUserLimit(10);
        item.setFulfillmentType(fulfillmentType);
        item.setStatus("ACTIVE");
        item.setOrganizationId(orgId);
        item.setCreatedBy(createdBy);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return rewardItemRepository.save(item);
    }

    private RewardOrder createOrder(Long rewardId) {
        return createOrder(rewardId, "PHYSICAL_SHIPMENT");
    }

    private RewardOrder createOrder(Long rewardId, String fulfillmentType) {
        RewardOrder order = new RewardOrder();
        order.setRewardId(rewardId);
        order.setAccountId(orgA_user.getId());
        order.setQuantity(1);
        order.setFulfillmentType(fulfillmentType);
        order.setStatus("ORDERED");
        order.setStatusChangedAt(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return rewardOrderRepository.save(order);
    }

    private WorkOrder createWorkOrder(Long createdBy, String orgId) {
        WorkOrder wo = new WorkOrder();
        wo.setTitle("Test WO");
        wo.setDescription("Test");
        wo.setOrganizationId(orgId);
        wo.setSeverity("MEDIUM");
        wo.setStatus("NEW_ALERT");
        wo.setCreatedBy(createdBy);
        wo.setCreatedAt(LocalDateTime.now());
        wo.setUpdatedAt(LocalDateTime.now());
        return workOrderRepository.save(wo);
    }

    private MockHttpSession sessionFor(Account account, RoleType role) {
        SessionAccount sa = new SessionAccount(account.getId(), account.getUsername(),
                role, RolePermissions.getPermissions(role), "ACTIVE");
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(SessionAuthenticationFilter.SESSION_ACCOUNT_KEY, sa);
        return s;
    }
}
