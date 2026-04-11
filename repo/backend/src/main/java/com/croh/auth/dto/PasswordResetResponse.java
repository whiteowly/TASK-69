package com.croh.auth.dto;

public record PasswordResetResponse(
    Long resetId,
    String status,
    boolean temporarySecretIssued,
    String temporarySecret
) {}
