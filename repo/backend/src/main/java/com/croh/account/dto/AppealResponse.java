package com.croh.account.dto;

import java.time.Instant;
import java.time.LocalDate;

public record AppealResponse(
    Long appealId,
    Long blacklistRecordId,
    Long accountId,
    String appealText,
    String contactNote,
    String status,
    LocalDate dueDate,
    Instant createdAt
) {}
