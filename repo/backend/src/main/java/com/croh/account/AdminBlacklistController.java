package com.croh.account;

import com.croh.account.dto.AppealDecisionRequest;
import com.croh.account.dto.AppealResponse;
import com.croh.account.dto.BlacklistRequest;
import com.croh.account.dto.BlacklistResponse;
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
@RequestMapping("/api/v1/admin")
public class AdminBlacklistController {

    private final BlacklistService blacklistService;

    public AdminBlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @PostMapping("/blacklist")
    @RequirePermission(Permission.MANAGE_BLACKLIST)
    public ResponseEntity<BlacklistResponse> blacklistAccount(@Valid @RequestBody BlacklistRequest request) {
        SessionAccount actor = getSessionAccount();
        BlacklistRecord record = blacklistService.blacklistAccount(
                request.targetAccountId(),
                request.reasonCode(),
                request.note(),
                actor.accountId(),
                actor.activeRole().name()
        );

        BlacklistResponse response = new BlacklistResponse(
                record.getId(),
                record.getAccountId(),
                record.getReasonCode(),
                record.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant()
        );
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/appeals")
    @RequirePermission(Permission.MANAGE_BLACKLIST)
    public ResponseEntity<List<AppealResponse>> listPendingAppeals() {
        List<BlacklistAppeal> appeals = blacklistService.listPendingAppeals();
        List<AppealResponse> responses = appeals.stream()
                .map(a -> new AppealResponse(
                        a.getId(),
                        a.getBlacklistRecordId(),
                        a.getAccountId(),
                        a.getAppealText(),
                        a.getContactNote(),
                        a.getStatus(),
                        a.getDueDate(),
                        a.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant()
                ))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/appeals/{appealId}/decision")
    @RequirePermission(Permission.MANAGE_BLACKLIST)
    public ResponseEntity<AppealResponse> decideAppeal(@PathVariable Long appealId,
                                                        @Valid @RequestBody AppealDecisionRequest request) {
        SessionAccount actor = getSessionAccount();
        BlacklistAppeal appeal = blacklistService.decideAppeal(
                appealId,
                request.decision(),
                request.decisionNote(),
                actor.accountId(),
                actor.activeRole().name()
        );

        AppealResponse response = new AppealResponse(
                appeal.getId(),
                appeal.getBlacklistRecordId(),
                appeal.getAccountId(),
                appeal.getAppealText(),
                appeal.getContactNote(),
                appeal.getStatus(),
                appeal.getDueDate(),
                appeal.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant()
        );
        return ResponseEntity.ok(response);
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
