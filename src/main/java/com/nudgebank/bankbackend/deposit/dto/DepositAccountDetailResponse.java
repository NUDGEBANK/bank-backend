package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DepositAccountDetailResponse(
    Long depositAccountId,
    Long depositProductId,
    String depositProductName,
    String depositProductType,
    String depositProductDescription,
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
    Long totalInstallmentCount,
    List<DepositPaymentScheduleResponse> schedules,
    List<DepositTransactionResponse> transactions
) {
}
