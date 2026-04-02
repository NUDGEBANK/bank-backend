package com.nudgebank.bankbackend.card.dto;

import java.math.BigDecimal;

public record CardIssueResponse(
    boolean ok,
    String message,
    Long accountId,
    String accountName,
    String accountNumber,
    BigDecimal balance,
    Long cardId,
    String cardNumber,
    String expiredYm,
    String cvc,
    String status
) {
}
