package com.croh.security;

import com.croh.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public PermissionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }

        if (annotation == null) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SessionAccount sessionAccount = null;
        if (auth != null && auth.getPrincipal() instanceof SessionAccount sa) {
            sessionAccount = sa;
        }

        if (sessionAccount == null) {
            writeForbidden(response, "Authentication required");
            return false;
        }

        for (Permission required : annotation.value()) {
            if (!sessionAccount.hasPermission(required)) {
                writeForbidden(response, "Insufficient permissions");
                return false;
            }
        }

        return true;
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = new ErrorResponse(
            "FORBIDDEN",
            message,
            List.of(),
            UUID.randomUUID().toString(),
            Instant.now()
        );
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
