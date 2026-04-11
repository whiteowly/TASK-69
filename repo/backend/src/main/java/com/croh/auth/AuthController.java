package com.croh.auth;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.account.BlacklistRecord;
import com.croh.account.BlacklistRecordRepository;
import com.croh.audit.AuditService;
import com.croh.auth.dto.LoginRequest;
import com.croh.auth.dto.LoginResponse;
import com.croh.auth.dto.RegisterRequest;
import com.croh.auth.dto.RegisterResponse;
import com.croh.common.DuplicateException;
import com.croh.common.ErrorResponse;
import com.croh.security.Permission;
import com.croh.security.SessionAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.croh.security.SessionAuthenticationFilter.SESSION_ACCOUNT_KEY;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountRepository accountRepository;
    private final BlacklistRecordRepository blacklistRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthController(AuthService authService,
                          AccountRepository accountRepository,
                          BlacklistRecordRepository blacklistRecordRepository,
                          PasswordEncoder passwordEncoder,
                          AuditService auditService) {
        this.authService = authService;
        this.accountRepository = accountRepository;
        this.blacklistRecordRepository = blacklistRecordRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        String normalizedUsername = request.username().toLowerCase();

        if (accountRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new DuplicateException("Username is already taken");
        }

        LocalDateTime now = LocalDateTime.now();

        Account account = new Account();
        account.setUsername(normalizedUsername);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setAccountType(request.accountType());
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setFailedLoginAttempts(0);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountRepository.save(account);

        auditService.log(account.getId(), "SYSTEM", "ACCOUNT_REGISTERED",
                "ACCOUNT", account.getId().toString(),
                null, "ACTIVE",
                null, UUID.randomUUID().toString());

        RegisterResponse response = new RegisterResponse(
                account.getId(),
                account.getUsername(),
                account.getStatus().name(),
                now.toInstant(ZoneOffset.UTC)
        );
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        SessionAccount session = authService.login(request.username(), request.password());

        // Session fixation protection: invalidate any existing session before creating a new one
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }

        // Create fresh session — blacklisted users get a constrained session
        // that only allows appeal submission (enforced by BlacklistEnforcementFilter)
        HttpSession httpSession = httpRequest.getSession(true);
        httpSession.setAttribute(SESSION_ACCOUNT_KEY, session);

        if ("BLACKLISTED".equals(session.accountStatus())) {
            // Return 423 per API spec, but the session cookie IS set so appeal endpoints work
            BlacklistRecord record = blacklistRecordRepository
                    .findTopByAccountIdOrderByCreatedAtDesc(session.accountId())
                    .orElse(null);

            ErrorResponse error = new ErrorResponse(
                    "ACCOUNT_BLACKLISTED",
                    "Account has been blacklisted. You may submit an appeal.",
                    List.of(),
                    UUID.randomUUID().toString(),
                    Instant.now()
            );
            return ResponseEntity.status(423).body(error);
        }

        return ResponseEntity.ok(buildLoginResponse(session));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SessionAccount session)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(buildLoginResponse(session));
    }

    private LoginResponse buildLoginResponse(SessionAccount session) {
        List<String> approvedRoles = authService.getApprovedRoleNames(session.accountId());
        Set<String> permissionNames = session.permissions().stream()
                .map(Permission::name)
                .collect(Collectors.toSet());

        return new LoginResponse(
                session.accountId(),
                session.username(),
                approvedRoles,
                session.activeRole().name(),
                permissionNames
        );
    }
}
