package com.croh.reporting.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportTemplateRequest(
    @NotBlank String name,
    String description,
    @NotBlank String metricIds,
    String filters,
    String format
) {}
