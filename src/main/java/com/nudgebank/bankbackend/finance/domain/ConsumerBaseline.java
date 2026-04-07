package com.nudgebank.bankbackend.finance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "consumer_baseline",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_consumer_baseline_member_month",
        columnNames = {"member_id", "analysis_year_month"}
    )
)
@Getter
@NoArgsConstructor
public class ConsumerBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "baseline_id")
    private Long baselineId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "avg_spending", precision = 15, scale = 2)
    private BigDecimal avgSpending;

    @Column(name = "essential_ratio", precision = 5, scale = 4)
    private BigDecimal essentialRatio;

    @Column(name = "normal_ratio", precision = 5, scale = 4)
    private BigDecimal normalRatio;

    @Column(name = "discretionary_ratio", precision = 5, scale = 4)
    private BigDecimal discretionaryRatio;

    @Column(name = "risk_ratio", precision = 5, scale = 4)
    private BigDecimal riskRatio;

    @Column(name = "volatility", precision = 15, scale = 2)
    private BigDecimal volatility;

    @Column(name = "volatility_index", precision = 5, scale = 4)
    private BigDecimal volatilityIndex;

    @Column(name = "analysis_year_month", nullable = false)
    private LocalDate analysisYearMonth;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public static ConsumerBaseline create(
            Long memberId,
            BigDecimal avgSpending,
            BigDecimal essentialRatio,
            BigDecimal normalRatio,
            BigDecimal discretionaryRatio,
            BigDecimal riskRatio,
            BigDecimal volatility,
            BigDecimal volatilityIndex,
            LocalDate analysisYearMonth,
            OffsetDateTime now
    ) {
        ConsumerBaseline baseline = new ConsumerBaseline();
        baseline.memberId = memberId;
        baseline.avgSpending = avgSpending;
        baseline.essentialRatio = essentialRatio;
        baseline.normalRatio = normalRatio;
        baseline.discretionaryRatio = discretionaryRatio;
        baseline.riskRatio = riskRatio;
        baseline.volatility = volatility;
        baseline.volatilityIndex = volatilityIndex;
        baseline.analysisYearMonth = analysisYearMonth;
        baseline.createdAt = now;
        baseline.updatedAt = now;
        return baseline;
    }

    public void update(
            BigDecimal avgSpending,
            BigDecimal essentialRatio,
            BigDecimal normalRatio,
            BigDecimal discretionaryRatio,
            BigDecimal riskRatio,
            BigDecimal volatility,
            BigDecimal volatilityIndex,
            LocalDate analysisYearMonth,
            OffsetDateTime now
    ) {
        this.avgSpending = avgSpending;
        this.essentialRatio = essentialRatio;
        this.normalRatio = normalRatio;
        this.discretionaryRatio = discretionaryRatio;
        this.riskRatio = riskRatio;
        this.volatility = volatility;
        this.volatilityIndex = volatilityIndex;
        this.analysisYearMonth = analysisYearMonth;
        this.updatedAt = now;
    }
}
