package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;

public record DepositAccountCreateRequest(
    Long depositProductId,
    Long accountId,
    BigDecimal joinAmount,
    Integer savingMonth,
    BigDecimal monthlyPaymentAmount,
    Boolean autoTransferYn,
    Integer autoTransferDay
) {
}
