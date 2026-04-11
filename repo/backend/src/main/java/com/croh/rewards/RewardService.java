package com.croh.rewards;

import com.croh.audit.AuditService;
import com.croh.crypto.EncryptionService;
import com.croh.security.OrgScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RewardService {

    private static final Pattern ZIP_PATTERN = Pattern.compile("^\\d{5}(-\\d{4})?$");
    private static final Pattern STATE_PATTERN = Pattern.compile("^[A-Z]{2}$");

    private static final Map<String, Set<String>> PHYSICAL_TRANSITIONS = Map.of(
            "ORDERED", Set.of("ALLOCATED"),
            "ALLOCATED", Set.of("PACKED"),
            "PACKED", Set.of("SHIPPED"),
            "SHIPPED", Set.of("DELIVERED")
    );

    private static final Map<String, Set<String>> VOUCHER_TRANSITIONS = Map.of(
            "ORDERED", Set.of("ALLOCATED"),
            "ALLOCATED", Set.of("VOUCHER_ISSUED"),
            "VOUCHER_ISSUED", Set.of("REDEEMED")
    );

    private static final Map<String, Set<String>> EXCEPTION_TRANSITIONS = Map.of(
            "OPEN", Set.of("UNDER_REVIEW"),
            "UNDER_REVIEW", Set.of("RESOLVED", "REJECTED")
    );

    private final RewardItemRepository rewardRepository;
    private final ShippingAddressRepository addressRepository;
    private final RewardOrderRepository orderRepository;
    private final FulfillmentExceptionRepository exceptionRepository;
    private final OrgScopeService orgScopeService;
    private final EncryptionService encryptionService;
    private final AuditService auditService;

    public RewardService(RewardItemRepository rewardRepository,
                         ShippingAddressRepository addressRepository,
                         RewardOrderRepository orderRepository,
                         FulfillmentExceptionRepository exceptionRepository,
                         OrgScopeService orgScopeService,
                         EncryptionService encryptionService,
                         AuditService auditService) {
        this.rewardRepository = rewardRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.exceptionRepository = exceptionRepository;
        this.orgScopeService = orgScopeService;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
    }

    public List<RewardOrder> listOrders(Long actorId, boolean isAdmin) {
        if (isAdmin) {
            return orderRepository.findAll();
        }
        List<String> orgIds = orgScopeService.getOrgScopes(actorId);
        if (orgIds.isEmpty()) return List.of();
        return orderRepository.findByRewardOrgIn(orgIds);
    }

    /**
     * Enforces that the actor is authorized to act on the given reward order,
     * based on the reward item's immutable organizationId.
     */
    private void enforceOrderScope(RewardOrder order, Long actorId, String actorRole) {
        RewardItem reward = rewardRepository.findById(order.getRewardId())
                .orElseThrow(() -> new IllegalArgumentException("Reward not found: " + order.getRewardId()));
        orgScopeService.enforceOrgScope(actorId, actorRole, reward.getOrganizationId());
    }

    @Transactional
    public RewardItem createReward(String title, String description, String tier,
                                    Integer inventoryCount, Integer perUserLimit,
                                    String fulfillmentType, String status,
                                    String organizationId,
                                    Long actorId, String actorRole) {
        orgScopeService.enforceOrgScope(actorId, actorRole, organizationId);

        RewardItem item = new RewardItem();
        item.setTitle(title);
        item.setOrganizationId(organizationId);
        item.setDescription(description);
        item.setTier(tier);
        item.setInventoryCount(inventoryCount != null ? inventoryCount : 0);
        item.setPerUserLimit(perUserLimit != null ? perUserLimit : 1);
        item.setFulfillmentType(fulfillmentType != null ? fulfillmentType : "PHYSICAL_SHIPMENT");
        item.setStatus(status != null ? status : "ACTIVE");
        item.setCreatedBy(actorId);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        RewardItem saved = rewardRepository.save(item);

        auditService.log(actorId, actorRole, "REWARD_CREATED",
                "RewardItem", saved.getId().toString(),
                null, saved.getStatus(), null, null);

        return saved;
    }

    public List<RewardItem> listRewards() {
        return rewardRepository.findByStatus("ACTIVE");
    }

    public RewardItem getReward(Long id) {
        return rewardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reward not found: " + id));
    }

    @Transactional
    public ShippingAddress createAddress(Long accountId, String line1, String line2,
                                          String city, String state, String zip) {
        if (!STATE_PATTERN.matcher(state).matches()) {
            throw new IllegalArgumentException("State must be a 2-character uppercase code");
        }
        if (!ZIP_PATTERN.matcher(zip).matches()) {
            throw new IllegalArgumentException("Zip code must be in 5-digit or 5+4 format");
        }

        // Auto-primary: first address for an account becomes primary
        List<ShippingAddress> existing = addressRepository.findByAccountId(accountId);
        boolean shouldBePrimary = existing.isEmpty();

        ShippingAddress addr = new ShippingAddress();
        addr.setAccountId(accountId);
        addr.setAddressLine1Encrypted(encryptionService.encrypt(line1));
        addr.setAddressLine2Encrypted(line2 != null ? encryptionService.encrypt(line2) : null);
        addr.setCity(city);
        addr.setStateCode(state);
        addr.setZipCode(zip);
        addr.setPrimary(shouldBePrimary);
        addr.setCreatedAt(LocalDateTime.now());

        ShippingAddress saved = addressRepository.save(addr);

        auditService.log(accountId, null, "ADDRESS_CREATED",
                "ShippingAddress", saved.getId().toString(),
                null, shouldBePrimary ? "PRIMARY" : null, null, null);

        return saved;
    }

    @Transactional
    public ShippingAddress setPrimaryAddress(Long accountId, Long addressId) {
        ShippingAddress target = addressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));
        if (!target.getAccountId().equals(accountId)) {
            throw new SecurityException("Cannot modify another user's address");
        }

        // Clear existing primary
        List<ShippingAddress> all = addressRepository.findByAccountId(accountId);
        for (ShippingAddress a : all) {
            if (a.isPrimary()) {
                a.setPrimary(false);
                addressRepository.save(a);
            }
        }

        target.setPrimary(true);
        ShippingAddress saved = addressRepository.save(target);

        auditService.log(accountId, null, "ADDRESS_SET_PRIMARY",
                "ShippingAddress", saved.getId().toString(),
                null, "PRIMARY", null, null);

        return saved;
    }

    public List<ShippingAddress> listAddresses(Long accountId) {
        return addressRepository.findByAccountId(accountId);
    }

    @Transactional
    public RewardOrder placeOrder(Long rewardId, Long accountId, Integer quantity,
                                   String fulfillmentType, Long addressId) {
        // Validate address ownership if shipping
        if (addressId != null) {
            ShippingAddress addr = addressRepository.findById(addressId)
                    .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));
            if (!addr.getAccountId().equals(accountId)) {
                throw new SecurityException("Cannot use another user's shipping address");
            }
        }

        RewardItem reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Reward not found: " + rewardId));

        int qty = quantity != null ? quantity : 1;

        // Check per-user limit
        long existingOrders = orderRepository.countByRewardIdAndAccountId(rewardId, accountId);
        if (existingOrders + qty > reward.getPerUserLimit()) {
            throw new IllegalStateException("Per-user limit exceeded for this reward");
        }

        // Check inventory
        if (reward.getInventoryCount() < qty) {
            throw new IllegalStateException("Insufficient inventory for this reward");
        }

        // Decrement inventory
        reward.setInventoryCount(reward.getInventoryCount() - qty);
        reward.setUpdatedAt(LocalDateTime.now());
        rewardRepository.save(reward);

        RewardOrder order = new RewardOrder();
        order.setRewardId(rewardId);
        order.setAccountId(accountId);
        order.setQuantity(qty);
        order.setFulfillmentType(fulfillmentType != null ? fulfillmentType : reward.getFulfillmentType());
        order.setShippingAddressId(addressId);
        order.setStatus("ORDERED");
        order.setStatusChangedAt(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        RewardOrder saved = orderRepository.save(order);

        auditService.log(accountId, null, "ORDER_PLACED",
                "RewardOrder", saved.getId().toString(),
                null, "ORDERED", null, null);

        return saved;
    }

    @Transactional
    public RewardOrder transitionOrder(Long orderId, String toState, String note,
                                        Long actorId, String actorRole) {
        RewardOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        enforceOrderScope(order, actorId, actorRole);

        String beforeState = order.getStatus();
        Map<String, Set<String>> transitions = "VOUCHER".equals(order.getFulfillmentType())
                ? VOUCHER_TRANSITIONS : PHYSICAL_TRANSITIONS;

        Set<String> allowedNext = transitions.get(beforeState);
        if (allowedNext == null || !allowedNext.contains(toState)) {
            throw new IllegalStateException(
                    "Invalid transition from " + beforeState + " to " + toState);
        }

        order.setStatus(toState);
        order.setStatusChangedAt(LocalDateTime.now());
        if (note != null) order.setNote(note);
        order.setUpdatedAt(LocalDateTime.now());

        RewardOrder saved = orderRepository.save(order);

        auditService.log(actorId, actorRole, "ORDER_TRANSITIONED",
                "RewardOrder", saved.getId().toString(),
                beforeState, toState, null, null);

        return saved;
    }

    @Transactional
    public RewardOrder setTracking(Long orderId, String trackingNumber,
                                    Long actorId, String actorRole) {
        RewardOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        enforceOrderScope(order, actorId, actorRole);
        if (!"SHIPPED".equals(order.getStatus())) {
            throw new IllegalStateException("Tracking can only be set for SHIPPED orders");
        }
        order.setTrackingNumber(trackingNumber);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    @Transactional
    public RewardOrder issueVoucher(Long orderId, String voucherCode,
                                     Long actorId, String actorRole) {
        RewardOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        enforceOrderScope(order, actorId, actorRole);
        if (!"VOUCHER_ISSUED".equals(order.getStatus())) {
            throw new IllegalStateException("Voucher can only be issued for VOUCHER_ISSUED status orders");
        }
        order.setVoucherCode(voucherCode);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    @Transactional
    public FulfillmentException createException(Long orderId, String reasonCode,
                                                 String description, Long actorId) {
        FulfillmentException exception = new FulfillmentException();
        exception.setOrderId(orderId);
        exception.setReasonCode(reasonCode);
        exception.setDescription(description);
        exception.setStatus("OPEN");
        exception.setCreatedBy(actorId);
        exception.setCreatedAt(LocalDateTime.now());
        exception.setUpdatedAt(LocalDateTime.now());

        FulfillmentException saved = exceptionRepository.save(exception);

        auditService.log(actorId, null, "EXCEPTION_CREATED",
                "FulfillmentException", saved.getId().toString(),
                null, "OPEN", reasonCode, null);

        return saved;
    }

    @Transactional
    public FulfillmentException transitionException(Long exceptionId, String toState,
                                                     Long actorId, String actorRole) {
        FulfillmentException exc = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Exception not found: " + exceptionId));

        String beforeState = exc.getStatus();
        Set<String> allowedNext = EXCEPTION_TRANSITIONS.get(beforeState);
        if (allowedNext == null || !allowedNext.contains(toState)) {
            throw new IllegalStateException(
                    "Invalid transition from " + beforeState + " to " + toState);
        }

        exc.setStatus(toState);
        if ("RESOLVED".equals(toState) || "REJECTED".equals(toState)) {
            exc.setResolvedBy(actorId);
        }
        exc.setUpdatedAt(LocalDateTime.now());

        FulfillmentException saved = exceptionRepository.save(exc);

        auditService.log(actorId, actorRole, "EXCEPTION_TRANSITIONED",
                "FulfillmentException", saved.getId().toString(),
                beforeState, toState, null, null);

        return saved;
    }

    @Transactional
    public FulfillmentException reopenException(Long exceptionId, String reasonCode, String note,
                                                 Long actorId, String actorRole) {
        FulfillmentException exc = exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Exception not found: " + exceptionId));

        if (!"RESOLVED".equals(exc.getStatus()) && !"REJECTED".equals(exc.getStatus())) {
            throw new IllegalStateException("Can only reopen from RESOLVED or REJECTED state");
        }

        String beforeState = exc.getStatus();
        exc.setStatus("OPEN");
        exc.setReopenReason(note);
        exc.setSupervisorApproval(true);
        exc.setResolvedBy(null);
        exc.setUpdatedAt(LocalDateTime.now());

        FulfillmentException saved = exceptionRepository.save(exc);

        auditService.log(actorId, actorRole, "EXCEPTION_REOPENED",
                "FulfillmentException", saved.getId().toString(),
                beforeState, "OPEN", reasonCode, null);

        return saved;
    }

    public List<RewardOrder> getOverdueOrders() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        return orderRepository.findOverdueOrders(List.of("PACKED", "SHIPPED"), sevenDaysAgo);
    }
}
