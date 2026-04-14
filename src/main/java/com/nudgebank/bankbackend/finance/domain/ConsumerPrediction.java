package com.nudgebank.bankbackend.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "consumer_prediction",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_consumer_prediction_member_month",
                columnNames = {"member_id", "analysis_year_month"}
        )
)
@Getter
@NoArgsConstructor
public class ConsumerPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prediction_id")
    private Long predictionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "analysis_year_month", nullable = false)
    private LocalDate analysisYearMonth;

    @Column(name = "predicted_year_month")
    private LocalDate predictedYearMonth;

    @Column(name = "predicted_total_spending", precision = 15, scale = 2)
    private BigDecimal predictedTotalSpending;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
