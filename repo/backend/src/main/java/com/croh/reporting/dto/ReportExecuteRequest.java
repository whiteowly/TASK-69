package com.croh.reporting.dto;

public record ReportExecuteRequest(
    String filters,
    String format
) {}
