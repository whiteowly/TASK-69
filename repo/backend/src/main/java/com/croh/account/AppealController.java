package com.croh.account;

import com.croh.account.dto.AppealRequest;
import com.croh.account.dto.AppealResponse;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/appeals")
public class AppealController {

    private final BlacklistService blacklistService;
    private final BlacklistRecordRepository blacklistRecordRepository;

    public AppealController(BlacklistService blacklistService,
                            BlacklistRecordRepository blacklistRecordRepository) {
        this.blacklistService = blacklistService;
        this.blacklistRecordRepository = blacklistRecordRepository;
    }

    @GetMapping("/my-blacklist")
    public ResponseEntity<?> getMyBlacklistInfo() {
        SessionAccount session = getSessionAccount();
        BlacklistRecord record = blacklistRecordRepository
                .findTopByAccountIdOrderByCreatedAtDesc(session.accountId())
                .orElse(null);

        if (record == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "blacklistId", record.getId(),
                "reasonCode", record.getReasonCode(),
                "createdAt", record.getCreatedAt().atZone(ZoneOffset.UTC).toInstant().toString()
        ));
    }

    @PostMapping
    public ResponseEntity<AppealResponse> submitAppeal(@Valid @RequestBody AppealRequest request) {
        SessionAccount session = getSessionAccount();

        BlacklistAppeal appeal = blacklistService.submitAppeal(
                session.accountId(),
                request.blacklistId(),
                request.appealText(),
                request.contactNote()
        );

        AppealResponse response = new AppealResponse(
                appeal.getId(),
                appeal.getBlacklistRecordId(),
                appeal.getAccountId(),
                appeal.getAppealText(),
                appeal.getContactNote(),
                appeal.getStatus(),
                appeal.getDueDate(),
                appeal.getCreatedAt().atZone(ZoneOffset.UTC).toInstant()
        );
        return ResponseEntity.status(201).body(response);
    }

    private SessionAccount getSessionAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (SessionAccount) auth.getPrincipal();
    }
}
