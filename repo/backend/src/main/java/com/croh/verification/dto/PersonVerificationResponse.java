package com.croh.verification.dto;

import java.time.Instant;

public record PersonVerificationResponse(
    Long verificationId,
    String status,
    Instant submittedAt
) {}
