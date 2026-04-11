package com.croh.rewards.dto;

import jakarta.validation.constraints.NotBlank;

public record VoucherRequest(
    @NotBlank String voucherCode
) {}
