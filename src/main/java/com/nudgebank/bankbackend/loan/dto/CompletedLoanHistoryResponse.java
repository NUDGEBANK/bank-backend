package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompletedLoanHistoryResponse(
    Long loanHistoryId,
    String productKey,
    String productName,
    String status,
    BigDecimal totalPrincipal,
    BigDecimal interestRate,
    String repaymentType,
    LocalDate startDate,
    LocalDate completedAt
) {
}
