package com.croh.auth;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.account.RoleMembership;
import com.croh.account.RoleMembershipRepository;
import com.croh.audit.AuditService;
import com.croh.common.AccountLockedException;
import com.croh.security.RoleType;
import com.croh.security.SessionAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private RoleMembershipRepository roleMembershipRepository;
    @Mock private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(accountRepository, roleMembershipRepository,
                passwordEncoder, auditService);
    }

    @Test
    void login_successful_returnsSessionAccount_noAuditForSuccess() {
        Account account = createAccount("testuser", "password123");
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(account));
        when(roleMembershipRepository.findByAccountIdAndStatus(eq(1L), eq(RoleMembership.RoleMembershipStatus.APPROVED)))
                .thenReturn(List.of());
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        SessionAccount result = authService.login("testuser", "password123");

        assertNotNull(result);
        assertEquals("ACTIVE", result.accountStatus());
        // Successful logins do not emit audit (only failures/security events)
        verify(auditService, never()).log(any(), any(), any(), any(), any(),
                any(), any(), any(), any());
    }

    @Test
    void login_wrongPassword_emitsLoginFailedAudit() {
        Account account = createAccount("testuser", "password123");
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        assertThrows(BadCredentialsException.class, () ->
                authService.login("testuser", "wrongpassword"));

        verify(auditService).log(eq(1L), isNull(), eq("LOGIN_FAILED"),
                eq("ACCOUNT"), eq("1"),
                isNull(), isNull(),
                eq("INVALID_PASSWORD"), anyString());
    }

    @Test
    void login_tenFailedAttempts_emitsLockoutAudit() {
        Account account = createAccount("testuser", "password123");
        account.setFailedLoginAttempts(9);
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        assertThrows(AccountLockedException.class, () ->
                authService.login("testuser", "wrongpassword"));

        assertEquals(Account.AccountStatus.LOCKED, account.getStatus());
        // Lockout audit emitted (not LOGIN_FAILED — lockout takes precedence)
        verify(auditService).log(eq(1L), isNull(), eq("ACCOUNT_LOCKOUT"),
                eq("ACCOUNT"), eq("1"),
                eq("ACTIVE"), eq("LOCKED"),
                eq("MAX_FAILED_ATTEMPTS"), anyString());
    }

    @Test
    void login_blacklistedUser_validPassword_emitsBlacklistedAudit() {
        Account account = createAccount("testuser", "password123");
        account.setStatus(Account.AccountStatus.BLACKLISTED);
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        SessionAccount result = authService.login("testuser", "password123");

        assertEquals("BLACKLISTED", result.accountStatus());
        assertTrue(result.permissions().isEmpty());

        verify(auditService).log(eq(1L), isNull(), eq("LOGIN_BLACKLISTED"),
                eq("ACCOUNT"), eq("1"),
                isNull(), eq("BLACKLISTED"),
                eq("CONSTRAINED_SESSION"), anyString());
    }

    @Test
    void login_lockedAccount_throwsAccountLockedException() {
        Account account = createAccount("testuser", "password123");
        account.setStatus(Account.AccountStatus.LOCKED);
        account.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        when(accountRepository.findByUsername("testuser")).thenReturn(Optional.of(account));

        assertThrows(AccountLockedException.class, () ->
                authService.login("testuser", "password123"));
    }

    private Account createAccount(String username, String rawPassword) {
        Account account = new Account();
        account.setId(1L);
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setAccountType(Account.AccountType.PERSON);
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setFailedLoginAttempts(0);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return account;
    }
}
