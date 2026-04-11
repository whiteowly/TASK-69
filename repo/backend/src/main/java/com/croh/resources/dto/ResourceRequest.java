package com.croh.resources.dto;

import jakarta.validation.constraints.NotBlank;

public record ResourceRequest(
    @NotBlank String type,
    @NotBlank String title,
    String description,
    Integer inventoryCount,
    String fileVersion,
    String organizationId,
    Long usagePolicyId,
    String status
) {}
