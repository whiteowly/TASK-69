package com.croh.resources.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PolicyRequest(
    @NotBlank String name,
    @NotBlank String scope,
    @NotNull Integer maxActions,
    @NotNull Integer windowDays,
    @NotBlank String resourceAction
) {}
