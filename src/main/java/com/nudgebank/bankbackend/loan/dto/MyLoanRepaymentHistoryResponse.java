package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MyLoanRepaymentHistoryResponse(
    Long repaymentId,
    BigDecimal repaymentAmount,
    BigDecimal repaymentRate,
    OffsetDateTime repaymentDatetime,
    BigDecimal remainingBalance,
    String reason
) {}
