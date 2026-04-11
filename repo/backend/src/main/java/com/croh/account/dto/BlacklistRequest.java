package com.croh.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BlacklistRequest(
    @NotNull Long targetAccountId,
    @NotBlank String reasonCode,
    String note
) {}
