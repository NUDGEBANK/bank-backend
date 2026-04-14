package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DepositTransactionResponse(
    Long depositTransactionId,
    Long depositPaymentScheduleId,
    String transactionType,
    BigDecimal amount,
    OffsetDateTime transactionDatetime,
    String status
) {
}
