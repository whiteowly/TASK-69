package com.croh.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppealRequest(
    @NotNull Long blacklistId,
    @NotBlank String appealText,
    String contactNote
) {}
