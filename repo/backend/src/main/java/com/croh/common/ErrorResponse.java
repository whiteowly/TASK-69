package com.croh.common;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    String code,
    String message,
    List<FieldError> fieldErrors,
    String correlationId,
    Instant timestamp
) {
    public record FieldError(String field, String reason, String message) {}
}
