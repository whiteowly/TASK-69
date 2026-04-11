package com.croh.resources.dto;

public record ClaimResponse(
    Long claimId,
    String result,
    String reasonCode,
    Long noticeId
) {}
