package com.croh.resources.dto;

public record DownloadResponse(
    Long downloadId,
    String result,
    String reasonCode
) {}
