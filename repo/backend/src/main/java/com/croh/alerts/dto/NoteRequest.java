package com.croh.alerts.dto;

import jakarta.validation.constraints.NotBlank;

public record NoteRequest(
    @NotBlank String content
) {}
