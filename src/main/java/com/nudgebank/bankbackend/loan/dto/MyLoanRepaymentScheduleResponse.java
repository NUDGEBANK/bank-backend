package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MyLoanRepaymentScheduleResponse(
    Long scheduleId,
    LocalDate dueDate,
    BigDecimal plannedPrincipal,
    BigDecimal plannedInterest,
    BigDecimal paidPrincipal,
    BigDecimal paidInterest,
    boolean settled,
    Integer overdueDays
) {}
