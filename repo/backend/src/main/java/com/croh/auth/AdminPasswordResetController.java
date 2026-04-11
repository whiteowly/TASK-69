package com.croh.auth;

import com.croh.auth.dto.PasswordResetRequest;
import com.croh.auth.dto.PasswordResetResponse;
import com.croh.security.Permission;
import com.croh.security.RequirePermission;
import com.croh.security.SessionAccount;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/password-resets")
public class AdminPasswordResetController {

    private final PasswordResetService passwordResetService;

    public AdminPasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping
    @RequirePermission(Permission.RESET_PASSWORD)
    public ResponseEntity<PasswordResetResponse> createPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        SessionAccount actor = (SessionAccount) auth.getPrincipal();

        PasswordResetResponse response = passwordResetService.createReset(
                request.targetAccountId(),
                request.identityReviewNote(),
                actor.accountId(),
                actor.activeRole().name()
        );

        return ResponseEntity.status(202).body(response);
    }
}
