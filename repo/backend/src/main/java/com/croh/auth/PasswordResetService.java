package com.croh.auth;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.auth.dto.PasswordResetResponse;
import com.croh.audit.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PasswordResetService(AccountRepository accountRepository,
                                PasswordResetRepository passwordResetRepository,
                                PasswordEncoder passwordEncoder,
                                AuditService auditService) {
        this.accountRepository = accountRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public PasswordResetResponse createReset(Long targetAccountId, String identityReviewNote,
                                              Long actorId, String actorRole) {
        Account account = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Account not found: " + targetAccountId));

        String temporaryPassword = generateTemporaryPassword();
        String hashedPassword = passwordEncoder.encode(temporaryPassword);

        account.setPasswordHash(hashedPassword);
        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);

        PasswordReset reset = new PasswordReset();
        reset.setTargetAccountId(targetAccountId);
        reset.setIdentityReviewNote(identityReviewNote);
        reset.setStatus("ISSUED");
        reset.setTemporarySecret(hashedPassword);
        reset.setCreatedBy(actorId);
        reset.setCreatedAt(LocalDateTime.now());
        passwordResetRepository.save(reset);

        auditService.log(actorId, actorRole, "PASSWORD_RESET",
                "ACCOUNT", targetAccountId.toString(),
                null, null,
                null, UUID.randomUUID().toString());

        return new PasswordResetResponse(
                reset.getId(),
                reset.getStatus(),
                true,
                temporaryPassword
        );
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
