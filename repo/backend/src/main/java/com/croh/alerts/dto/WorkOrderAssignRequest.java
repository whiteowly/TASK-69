package com.croh.alerts.dto;

import jakarta.validation.constraints.NotNull;

public record WorkOrderAssignRequest(
    @NotNull Long assigneeId
) {}
