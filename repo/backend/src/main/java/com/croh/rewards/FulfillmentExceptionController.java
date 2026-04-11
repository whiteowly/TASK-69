package com.croh.rewards;

import com.croh.rewards.dto.ExceptionRequest;
import com.croh.rewards.dto.ExceptionReopenRequest;
import com.croh.rewards.dto.ExceptionResponse;
import com.croh.rewards.dto.ExceptionTransitionRequest;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fulfillment-exceptions")
public class FulfillmentExceptionController {

    private final RewardService rewardService;

    public FulfillmentExceptionController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @PostMapping
    @RequirePermission(Permission.MANAGE_REWARD_FULFILLMENT)
    public ResponseEntity<ExceptionResponse> createException(@Valid @RequestBody ExceptionRequest request) {
        SessionAccount actor = getSessionAccount();
        FulfillmentException exc = rewardService.createException(
                request.orderId(), request.reasonCode(), request.description(),
                actor.accountId());
        return ResponseEntity.status(201).body(toResponse(exc));
    }

    @PostMapping("/{id}/transition")
    @RequirePermission(Permission.MANAGE_REWARD_FULFILLMENT)
    public ResponseEntity<ExceptionResponse> transitionException(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionTransitionRequest request) {
        SessionAccount actor = getSessionAccount();
        FulfillmentException exc = rewardService.transitionException(
                id, request.toState(), actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(exc));
    }

    @PostMapping("/{id}/reopen")
    @RequirePermission(Permission.APPROVE_EXCEPTION_REOPEN)
    public ResponseEntity<ExceptionResponse> reopenException(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionReopenRequest request) {
        SessionAccount actor = getSessionAccount();
        FulfillmentException exc = rewardService.reopenException(
                id, request.reasonCode(), request.note(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.ok(toResponse(exc));
    }

    private ExceptionResponse toResponse(FulfillmentException e) {
        return new ExceptionResponse(e.getId(), e.getOrderId(), e.getReasonCode(),
                e.getDescription(), e.getStatus(), e.isSupervisorApproval(),
                e.getReopenReason(), e.getCreatedBy(), e.getResolvedBy(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
