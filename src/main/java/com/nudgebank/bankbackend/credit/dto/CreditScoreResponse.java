package com.nudgebank.bankbackend.credit.dto;

import java.util.List;

public record CreditScoreResponse(
    boolean success,
    String message,
    Integer creditScore,
    String creditGrade,
    String evaluationResult,
    String evaluatedAt,
    Integer scoreChange,
    Long estimatedLoanLimit,
    List<CreditFactorDto> factors,
    List<RecommendedLoanDto> recommendedLoans
) {
  public record CreditFactorDto(
      String title,
      String value,
      String description
  ) {
  }

  public record RecommendedLoanDto(
      String id,
      String name,
      String rate,
      String limit,
      String reason
  ) {
  }
}
