package com.croh.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces eager resolution of the CSRF token so that CookieCsrfTokenRepository
 * writes the XSRF-TOKEN cookie on every response. Without this, Spring Security 6
 * defers token generation and the SPA may never receive the cookie.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Accessing getToken() forces deferred token generation,
            // which causes the CookieCsrfTokenRepository to set the cookie.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
