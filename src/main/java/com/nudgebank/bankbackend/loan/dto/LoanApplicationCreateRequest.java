package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;

public record LoanApplicationCreateRequest(
    String productKey,
    BigDecimal loanAmount,
    String loanTerm,
    BigDecimal monthlyIncome,
    Integer salaryDate,
    String purpose
) {}
