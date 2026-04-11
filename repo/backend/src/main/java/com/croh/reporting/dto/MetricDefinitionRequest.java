package com.croh.reporting.dto;

import jakarta.validation.constraints.NotBlank;

public record MetricDefinitionRequest(
    @NotBlank String name,
    String description,
    @NotBlank String queryTemplate,
    @NotBlank String domain
) {}
