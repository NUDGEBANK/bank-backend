package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MyLoanSummaryResponse(
    Long loanHistoryId,
    String status,
    BigDecimal totalPrincipal,
    BigDecimal remainingPrincipal,
    BigDecimal repaidPrincipal,
    BigDecimal interestRate,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate nextPaymentDate,
    BigDecimal nextPaymentAmount,
    BigDecimal cumulativeInterest,
    String repaymentAccountNumber
) {}
