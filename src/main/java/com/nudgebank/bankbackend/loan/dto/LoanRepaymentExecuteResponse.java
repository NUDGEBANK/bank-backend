package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;

public record LoanRepaymentExecuteResponse(
    BigDecimal repaymentAmount,
    BigDecimal paidPrincipal,
    BigDecimal paidInterest,
    BigDecimal overdueInterest,
    BigDecimal remainingPrincipal,
    String loanStatus,
    boolean autoTransferred
) {}
