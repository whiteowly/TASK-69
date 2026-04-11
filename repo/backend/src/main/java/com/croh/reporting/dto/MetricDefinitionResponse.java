package com.croh.reporting.dto;

import java.time.LocalDateTime;

public record MetricDefinitionResponse(
    Long id,
    String name,
    String description,
    String queryTemplate,
    String domain,
    Long createdBy,
    LocalDateTime createdAt
) {}
