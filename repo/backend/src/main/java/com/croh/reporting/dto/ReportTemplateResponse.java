package com.croh.reporting.dto;

import java.time.LocalDateTime;

public record ReportTemplateResponse(
    Long id,
    String name,
    String description,
    String metricIds,
    String defaultFilters,
    String outputFormat,
    Long createdBy,
    LocalDateTime createdAt
) {}
