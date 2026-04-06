package com.nudgebank.bankbackend.credit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "credit_history")
@Getter
@NoArgsConstructor
public class CreditHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "credit_history_id")
  private Long creditHistoryId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "credit_score")
  private Integer creditScore;

  @Column(name = "credit_grade", length = 10)
  private String creditGrade;

  @Column(name = "evaluation_result", columnDefinition = "TEXT")
  private String evaluationResult;

  @Column(name = "evaluated_at")
  private LocalDateTime evaluatedAt;

  public static CreditHistory create(
      Long memberId,
      Integer creditScore,
      String creditGrade,
      String evaluationResult,
      LocalDateTime evaluatedAt
  ) {
    CreditHistory creditHistory = new CreditHistory();
    creditHistory.memberId = memberId;
    creditHistory.creditScore = creditScore;
    creditHistory.creditGrade = creditGrade;
    creditHistory.evaluationResult = evaluationResult;
    creditHistory.evaluatedAt = evaluatedAt;
    return creditHistory;
  }
}
