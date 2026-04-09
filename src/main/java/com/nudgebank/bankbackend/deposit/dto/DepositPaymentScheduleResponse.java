package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DepositPaymentScheduleResponse(
    Long depositPaymentScheduleId,
    Integer installmentNo,
    LocalDate dueDate,
    BigDecimal plannedAmount,
    BigDecimal paidAmount,
    OffsetDateTime paidAt,
    Boolean isPaid,
    Boolean autoTransferYn,
    Integer autoTransferDay,
    String autoTransferStatus
) {
}
