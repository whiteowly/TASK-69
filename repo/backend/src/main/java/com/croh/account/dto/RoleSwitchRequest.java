package com.croh.account.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleSwitchRequest(
    @NotBlank String role,
    String scopeId
) {}
