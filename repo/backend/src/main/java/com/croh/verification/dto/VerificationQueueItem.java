package com.croh.verification.dto;

import java.time.Instant;

public record VerificationQueueItem(
    String type,
    Long id,
    Long accountId,
    String status,
    String legalName,
    String dobMasked,
    String fileName,
    Long fileSize,
    String contentType,
    boolean duplicateFlag,
    Instant createdAt
) {}
