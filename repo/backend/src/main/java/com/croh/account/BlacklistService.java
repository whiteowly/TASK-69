package com.croh.account;

import com.croh.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BlacklistService {

    private final AccountRepository accountRepository;
    private final BlacklistRecordRepository blacklistRecordRepository;
    private final BlacklistAppealRepository blacklistAppealRepository;
    private final AuditService auditService;

    public BlacklistService(AccountRepository accountRepository,
                            BlacklistRecordRepository blacklistRecordRepository,
                            BlacklistAppealRepository blacklistAppealRepository,
                            AuditService auditService) {
        this.accountRepository = accountRepository;
        this.blacklistRecordRepository = blacklistRecordRepository;
        this.blacklistAppealRepository = blacklistAppealRepository;
        this.auditService = auditService;
    }

    @Transactional
    public BlacklistRecord blacklistAccount(Long targetAccountId, String reasonCode, String note,
                                            Long actorId, String actorRole) {
        Account account = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + targetAccountId));

        account.setStatus(Account.AccountStatus.BLACKLISTED);
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);

        BlacklistRecord record = new BlacklistRecord();
        record.setAccountId(targetAccountId);
        record.setReasonCode(reasonCode);
        record.setNote(note);
        record.setCreatedBy(actorId);
        record.setCreatedAt(LocalDateTime.now());
        blacklistRecordRepository.save(record);

        auditService.log(actorId, actorRole, "ACCOUNT_BLACKLISTED",
                "ACCOUNT", targetAccountId.toString(),
                null, "BLACKLISTED",
                reasonCode, UUID.randomUUID().toString());

        return record;
    }

    @Transactional
    public BlacklistAppeal submitAppeal(Long accountId, Long blacklistRecordId,
                                        String appealText, String contactNote) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        if (account.getStatus() != Account.AccountStatus.BLACKLISTED) {
            throw new IllegalStateException("Account is not blacklisted");
        }

        BlacklistRecord record = blacklistRecordRepository.findById(blacklistRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Blacklist record not found: " + blacklistRecordId));

        if (!record.getAccountId().equals(accountId)) {
            throw new IllegalArgumentException("Blacklist record does not belong to this account");
        }

        BlacklistAppeal appeal = new BlacklistAppeal();
        appeal.setBlacklistRecordId(blacklistRecordId);
        appeal.setAccountId(accountId);
        appeal.setAppealText(appealText);
        appeal.setContactNote(contactNote);
        appeal.setStatus("PENDING");
        appeal.setDueDate(computeDueDate(LocalDate.now()));
        appeal.setCreatedAt(LocalDateTime.now());
        blacklistAppealRepository.save(appeal);

        auditService.log(accountId, "PARTICIPANT", "APPEAL_SUBMITTED",
                "BLACKLIST_APPEAL", appeal.getId().toString(),
                null, "PENDING",
                null, UUID.randomUUID().toString());

        return appeal;
    }

    @Transactional
    public BlacklistAppeal decideAppeal(Long appealId, String decision, String decisionNote,
                                        Long actorId, String actorRole) {
        if (!"APPROVE_UNBLOCK".equals(decision) && !"DENY".equals(decision)) {
            throw new IllegalArgumentException("Decision must be APPROVE_UNBLOCK or DENY");
        }

        BlacklistAppeal appeal = blacklistAppealRepository.findByIdAndStatus(appealId, "PENDING")
                .orElseThrow(() -> new IllegalStateException("Appeal not found or not in PENDING status"));

        appeal.setStatus(decision);
        appeal.setDecidedBy(actorId);
        appeal.setDecidedAt(LocalDateTime.now());
        appeal.setDecisionNote(decisionNote);
        blacklistAppealRepository.save(appeal);

        if ("APPROVE_UNBLOCK".equals(decision)) {
            Account account = accountRepository.findById(appeal.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Account not found"));
            account.setStatus(Account.AccountStatus.ACTIVE);
            account.setUpdatedAt(LocalDateTime.now());
            accountRepository.save(account);
        }

        auditService.log(actorId, actorRole, "APPEAL_DECIDED",
                "BLACKLIST_APPEAL", appealId.toString(),
                "PENDING", decision,
                null, UUID.randomUUID().toString());

        return appeal;
    }

    public LocalDate computeDueDate(LocalDate from) {
        LocalDate date = from;
        int businessDays = 0;
        while (businessDays < 3) {
            date = date.plusDays(1);
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                businessDays++;
            }
        }
        return date;
    }

    public List<BlacklistAppeal> listPendingAppeals() {
        return blacklistAppealRepository.findByStatus("PENDING");
    }
}
