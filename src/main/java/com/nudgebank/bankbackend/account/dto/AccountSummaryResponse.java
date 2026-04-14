package com.nudgebank.bankbackend.account.dto;

import java.math.BigDecimal;

public record AccountSummaryResponse(
    Long accountId,
    String accountName,
    String accountNumber,
    BigDecimal balance,
    BigDecimal protectedBalance
) {
}
