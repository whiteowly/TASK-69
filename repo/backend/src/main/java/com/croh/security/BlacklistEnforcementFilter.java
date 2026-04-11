package com.croh.security;

import com.croh.account.Account;
import com.croh.account.AccountRepository;
import com.croh.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Checks the database on every authenticated request to enforce blacklist status,
 * even if the user had an active session when they were blacklisted.
 *
 * Exempt paths: logout and appeal submission (blacklisted users must still be able to
 * log out and submit appeals per the design spec).
 */
public class BlacklistEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/v1/auth/logout",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/appeals"
    );

    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    public BlacklistEnforcementFilter(AccountRepository accountRepository, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip exempt paths
        for (String exempt : EXEMPT_PATHS) {
            if (path.equals(exempt) || path.startsWith(exempt + "/")) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SessionAccount sessionAccount) {
            Account account = accountRepository.findById(sessionAccount.accountId()).orElse(null);
            if (account != null && account.getStatus() == Account.AccountStatus.BLACKLISTED) {
                // Update session to reflect blacklisted status
                HttpSession session = request.getSession(false);
                if (session != null) {
                    SessionAccount updated = new SessionAccount(
                            sessionAccount.accountId(),
                            sessionAccount.username(),
                            sessionAccount.activeRole(),
                            sessionAccount.permissions(),
                            "BLACKLISTED"
                    );
                    session.setAttribute(SessionAuthenticationFilter.SESSION_ACCOUNT_KEY, updated);
                }

                response.setStatus(423);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorResponse error = new ErrorResponse(
                        "ACCOUNT_BLACKLISTED",
                        "Your account has been blacklisted. You may submit an appeal.",
                        List.of(),
                        UUID.randomUUID().toString(),
                        Instant.now()
                );
                objectMapper.writeValue(response.getOutputStream(), error);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
