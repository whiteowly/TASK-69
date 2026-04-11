package com.croh.rewards;

import com.croh.rewards.dto.OrderRequest;
import com.croh.rewards.dto.OrderResponse;
import com.croh.rewards.dto.OrderTransitionRequest;
import com.croh.rewards.dto.TrackingRequest;
import com.croh.rewards.dto.VoucherRequest;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reward-orders")
public class RewardOrderController {

    private final RewardService rewardService;

    public RewardOrderController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @GetMapping
    @RequirePermission(Permission.MANAGE_REWARD_FULFILLMENT)
    public ResponseEntity<List<OrderResponse>> listOrders() {
        SessionAccount actor = getSessionAccount();
        boolean isAdmin = actor.activeRole() == com.croh.security.RoleType.ADMIN;
        List<OrderResponse> orders = rewardService.listOrders(actor.accountId(), isAdmin).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        SessionAccount actor = getSessionAccount();
        RewardOrder order = rewardService.placeOrder(
                request.rewardId(), actor.accountId(), request.quantity(),
                request.fulfillmentType(), request.addressId());
        return ResponseEntity.status(201).body(toResponse(order));
    }

    @PostMapping("/{id}/transition")
    @RequirePermission(Permission.MANAGE_REWARD_FULFILLMENT)
    public ResponseEntity<OrderResponse> transitionOrder(@PathVariable Long id,
                                                          @Valid @RequestBody OrderTransitionRequest request) {
        SessionAccount actor = getSessionAccount();
        RewardOrder order = rewardService.transitionOrder(id, request.toState(), request.note(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(order));
    }

    @PostMapping("/{id}/tracking")
    @RequirePermission(Permission.MANAGE_REWARD_FULFILLMENT)
    public ResponseEntity<OrderResponse> setTracking(@PathVariable Long id,
                                                      @Valid @RequestBody TrackingRequest request) {
        SessionAccount actor = getSessionAccount();
        RewardOrder order = rewardService.setTracking(id, request.trackingNumber(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(order));
    }

    @PostMapping("/{id}/voucher")
    @RequirePermission(Permission.MANAGE_REWARD_FULFILLMENT)
    public ResponseEntity<OrderResponse> issueVoucher(@PathVariable Long id,
                                                       @Valid @RequestBody VoucherRequest request) {
        SessionAccount actor = getSessionAccount();
        RewardOrder order = rewardService.issueVoucher(id, request.voucherCode(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(order));
    }

    private OrderResponse toResponse(RewardOrder o) {
        return new OrderResponse(o.getId(), o.getRewardId(), o.getAccountId(), o.getQuantity(),
                o.getFulfillmentType(), o.getShippingAddressId(), o.getStatus(),
                o.getTrackingNumber(), o.getVoucherCode(), o.getNote(),
                o.getCreatedAt(), o.getUpdatedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
