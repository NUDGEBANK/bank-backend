package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;

public record LoanRepaymentExecuteRequest(
    String productKey,
    BigDecimal amount
) {}
