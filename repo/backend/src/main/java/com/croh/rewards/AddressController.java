package com.croh.rewards;

import com.croh.rewards.dto.AddressRequest;
import com.croh.rewards.dto.AddressResponse;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts/me/addresses")
public class AddressController {

    private final RewardService rewardService;

    public AddressController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponse>> listAddresses() {
        SessionAccount actor = getSessionAccount();
        List<AddressResponse> addresses = rewardService.listAddresses(actor.accountId()).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(addresses);
    }

    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(@Valid @RequestBody AddressRequest request) {
        SessionAccount actor = getSessionAccount();
        ShippingAddress addr = rewardService.createAddress(
                actor.accountId(), request.line1(), request.line2(),
                request.city(), request.state(), request.zip());
        return ResponseEntity.status(201).body(toResponse(addr));
    }

    @PutMapping("/{id}/primary")
    public ResponseEntity<AddressResponse> setPrimary(@PathVariable Long id) {
        SessionAccount actor = getSessionAccount();
        ShippingAddress addr = rewardService.setPrimaryAddress(actor.accountId(), id);
        return ResponseEntity.ok(toResponse(addr));
    }

    private AddressResponse toResponse(ShippingAddress addr) {
        return new AddressResponse(
                addr.getId(), addr.getAccountId(), addr.getCity(),
                addr.getStateCode(), addr.getZipCode(), addr.isPrimary(),
                addr.getCreatedAt());
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
