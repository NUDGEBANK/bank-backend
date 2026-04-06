package com.nudgebank.bankbackend.card.dto;

import java.math.BigDecimal;
import java.util.List;

public record CardHistoryResponse(
    boolean ok,
    String message,
    List<CardHistoryAccountDto> accounts
) {
  public record CardHistoryAccountDto(
      Long accountId,
      String accountName,
      String accountNumber,
      BigDecimal balance,
      Long cardId,
      String cardNumber,
      String expiredYm,
      String cardStatus,
      BigDecimal spentThisMonth,
      List<CardHistoryTransactionDto> transactions
  ) {
  }

  public record CardHistoryTransactionDto(
      Long transactionId,
      String marketName,
      String categoryName,
      BigDecimal amount,
      String transactionDatetime,
      String menuName,
      Integer quantity
  ) {
  }
}
