package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MyLoanRepaymentHistoryResponse(
    Long repaymentId,
    BigDecimal repaymentAmount,
    BigDecimal repaymentRate,
    OffsetDateTime repaymentDatetime,
    BigDecimal remainingBalance,
    String reason,
    TransactionInfo transaction
) {
    public record TransactionInfo(
        Long transactionId,
        Long cardId,
        Long marketId,
        Long categoryId,
        String qrId,
        BigDecimal amount,
        OffsetDateTime transactionDatetime,
        String menuName,
        Integer quantity
    ) {}
}
