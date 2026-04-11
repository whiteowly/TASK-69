package com.croh.reporting.dto;

import java.time.LocalDateTime;

public record ReportExecutionResponse(
    Long id,
    Long templateId,
    String filters,
    String outputFormat,
    String status,
    String resultData,
    String exportFilePath,
    Long executedBy,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {}
