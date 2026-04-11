package com.croh.rewards;

import com.croh.rewards.dto.RewardRequest;
import com.croh.rewards.dto.RewardResponse;
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
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @PostMapping
    @RequirePermission(Permission.MANAGE_REWARDS)
    public ResponseEntity<RewardResponse> createReward(@Valid @RequestBody RewardRequest request) {
        SessionAccount actor = getSessionAccount();
        RewardItem item = rewardService.createReward(
                request.title(), request.description(), request.tier(),
                request.inventoryCount(), request.perUserLimit(),
                request.fulfillmentType(), request.status(),
                request.organizationId(),
                actor.accountId(), actor.activeRole().name());
        return ResponseEntity.status(201).body(toResponse(item));
    }

    @GetMapping
    public ResponseEntity<List<RewardResponse>> listRewards() {
        List<RewardResponse> rewards = rewardService.listRewards().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(rewards);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RewardResponse> getReward(@PathVariable Long id) {
        RewardItem item = rewardService.getReward(id);
        return ResponseEntity.ok(toResponse(item));
    }

    private RewardResponse toResponse(RewardItem r) {
        return new RewardResponse(r.getId(), r.getTitle(), r.getDescription(), r.getTier(),
                r.getInventoryCount(), r.getPerUserLimit(), r.getFulfillmentType(),
                r.getStatus(), r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
