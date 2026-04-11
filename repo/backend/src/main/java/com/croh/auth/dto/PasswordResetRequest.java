package com.croh.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasswordResetRequest(
    @NotNull Long targetAccountId,
    @NotBlank String identityReviewNote
) {}
