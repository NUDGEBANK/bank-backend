package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;

public record DepositAccountActionResponse(
    Long depositAccountId,
    String status,
    BigDecimal currentBalance,
    BigDecimal processedAmount,
    String message
) {
}
