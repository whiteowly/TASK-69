package com.croh.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridges the custom HttpSession SESSION_ACCOUNT attribute into Spring Security's
 * SecurityContext so that .authenticated() rules and the rest of the filter chain
 * see a properly authenticated principal.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String SESSION_ACCOUNT_KEY = "SESSION_ACCOUNT";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                SessionAccount account = (SessionAccount) session.getAttribute(SESSION_ACCOUNT_KEY);
                if (account != null) {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + account.activeRole().name()));
                    for (Permission p : account.permissions()) {
                        authorities.add(new SimpleGrantedAuthority(p.name()));
                    }
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(account, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
