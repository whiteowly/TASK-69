package com.croh.resources.dto;

import java.time.LocalDateTime;

public record ResourceResponse(
    Long id,
    String type,
    String title,
    String description,
    Integer inventoryCount,
    String fileVersion,
    Long fileSize,
    String contentType,
    Long usagePolicyId,
    String status,
    Long createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
