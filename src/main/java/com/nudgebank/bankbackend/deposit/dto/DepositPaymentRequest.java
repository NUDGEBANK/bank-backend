package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;

public record DepositPaymentRequest(
    BigDecimal amount
) {
    public DepositPaymentRequest {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount는 0보다 커야 합니다.");
        }
    }
}
