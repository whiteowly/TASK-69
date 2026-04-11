package com.croh.auth;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
import com.croh.audit.AuditService;
import com.croh.common.AccountLockedException;
import com.croh.security.Permission;
import com.croh.security.RolePermissions;
import com.croh.security.RoleType;
import com.croh.security.SessionAccount;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final int LOCKOUT_MINUTES = 30;

    private final AccountRepository accountRepository;
    private final RoleMembershipRepository roleMembershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(AccountRepository accountRepository,
                       RoleMembershipRepository roleMembershipRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.accountRepository = accountRepository;
        this.roleMembershipRepository = roleMembershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Authenticates a user. NOT @Transactional — each DB write and audit call
     * commits independently so that failed-login counters, lockout state changes,
     * and audit records persist even when this method throws.
     */
    public SessionAccount login(String username, String password) {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        // Check lockout (locked users must wait — no session)
        if (account.getStatus() == Account.AccountStatus.LOCKED
                && account.getLockedUntil() != null
                && account.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException("Account is locked", account.getLockedUntil());
        }

        // If lock expired, reset
        if (account.getStatus() == Account.AccountStatus.LOCKED
                && (account.getLockedUntil() == null || account.getLockedUntil().isBefore(LocalDateTime.now()))) {
            account.setStatus(Account.AccountStatus.ACTIVE);
            account.setFailedLoginAttempts(0);
            account.setLockedUntil(null);
            accountRepository.save(account);
        }

        // Verify password — required for ALL statuses including blacklisted
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            int attempts = account.getFailedLoginAttempts() + 1;
            account.setFailedLoginAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                account.setStatus(Account.AccountStatus.LOCKED);
                account.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                accountRepository.save(account);

                auditService.log(account.getId(), null, "ACCOUNT_LOCKOUT",
                        "ACCOUNT", account.getId().toString(),
                        "ACTIVE", "LOCKED",
                        "MAX_FAILED_ATTEMPTS", UUID.randomUUID().toString());

                throw new AccountLockedException(
                        "Account locked due to too many failed attempts",
                        account.getLockedUntil()
                );
            }

            accountRepository.save(account);

            auditService.log(account.getId(), null, "LOGIN_FAILED",
                    "ACCOUNT", account.getId().toString(),
                    null, null,
                    "INVALID_PASSWORD", UUID.randomUUID().toString());

            throw new BadCredentialsException("Invalid username or password");
        }

        // Password correct — reset failed attempts (even for blacklisted)
        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        accountRepository.save(account);

        // For blacklisted accounts: return constrained session with zero permissions
        if (account.getStatus() == Account.AccountStatus.BLACKLISTED) {
            auditService.log(account.getId(), null, "LOGIN_BLACKLISTED",
                    "ACCOUNT", account.getId().toString(),
                    null, "BLACKLISTED",
                    "CONSTRAINED_SESSION", UUID.randomUUID().toString());

            return new SessionAccount(
                    account.getId(),
                    account.getUsername(),
                    RoleType.PARTICIPANT,
                    Collections.emptySet(),
                    "BLACKLISTED"
            );
        }

        // Normal active login
        account.setStatus(Account.AccountStatus.ACTIVE);
        accountRepository.save(account);

        List<RoleMembership> approvedRoles = roleMembershipRepository
                .findByAccountIdAndStatus(account.getId(), RoleMembership.RoleMembershipStatus.APPROVED);

        RoleType activeRole = determineActiveRole(approvedRoles);
        Set<Permission> permissions = RolePermissions.getPermissions(activeRole);

        return new SessionAccount(
                account.getId(),
                account.getUsername(),
                activeRole,
                permissions,
                account.getStatus().name()
        );
    }

    private RoleType determineActiveRole(List<RoleMembership> approvedRoles) {
        RoleType highest = RoleType.PARTICIPANT;
        for (RoleMembership rm : approvedRoles) {
            try {
                RoleType rt = RoleType.valueOf(rm.getRoleType());
                if (rt.ordinal() > highest.ordinal()) {
                    highest = rt;
                }
            } catch (IllegalArgumentException e) {
                // skip unknown role types
            }
        }
        return highest;
    }

    public List<String> getApprovedRoleNames(Long accountId) {
        return roleMembershipRepository
                .findByAccountIdAndStatus(accountId, RoleMembership.RoleMembershipStatus.APPROVED)
                .stream()
                .map(RoleMembership::getRoleType)
                .toList();
    }
}
