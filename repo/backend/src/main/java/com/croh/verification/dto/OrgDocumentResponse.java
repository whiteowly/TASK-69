package com.croh.verification.dto;

public record OrgDocumentResponse(
    Long documentId,
    String status,
    boolean duplicateChecksumFlag
) {}
