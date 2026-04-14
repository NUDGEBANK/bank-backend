package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DepositAccountSummaryResponse(
    Long depositAccountId,
    Long depositProductId,
    String depositProductName,
    String depositProductType,
    Long linkedAccountId,
    String linkedAccountNumber,
    String depositAccountNumber,
    BigDecimal joinAmount,
    BigDecimal currentBalance,
    BigDecimal interestRate,
    Integer savingMonth,
    LocalDate startDate,
    LocalDate maturityDate,
    String status,
    Long paidInstallmentCount,
    Long totalInstallmentCount
) {
}
