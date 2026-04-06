package com.nudgebank.bankbackend.credit.dto;

import java.util.List;

public record CreditHistoryListResponse(
    boolean success,
    String message,
    List<CreditHistoryItemDto> histories
) {
  public record CreditHistoryItemDto(
      Long creditHistoryId,
      Integer creditScore,
      String creditGrade,
      String evaluationResult,
      String evaluatedAt
  ) {
  }
}
