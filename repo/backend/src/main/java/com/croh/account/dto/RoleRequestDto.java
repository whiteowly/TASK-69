package com.croh.account.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleRequestDto(
    @NotBlank String role,
    String scopeType,
    String scopeId
) {}
