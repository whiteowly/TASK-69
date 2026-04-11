package com.croh.events.dto;

import java.time.LocalDateTime;

public record RosterEntry(
    Long registrationId,
    Long accountId,
    String status,
    LocalDateTime registeredAt
) {}
