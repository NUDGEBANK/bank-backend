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
        name = "consumer_monthly_analysis",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_consumer_monthly_analysis_member_month",
                columnNames = {"member_id", "analysis_year_month"}
        )
)
@Getter
@NoArgsConstructor
public class ConsumerMonthlyAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "analysis_year_month", nullable = false)
    private LocalDate analysisYearMonth;

    @Column(name = "current_month_spending", precision = 15, scale = 2)
    private BigDecimal currentMonthSpending;

    @Column(name = "same_day_avg_spending", precision = 15, scale = 2)
    private BigDecimal sameDayAvgSpending;

    @Column(name = "spending_diff_amount", precision = 15, scale = 2)
    private BigDecimal spendingDiffAmount;

    @Column(name = "spending_status", length = 50)
    private String spendingStatus;

    @Column(name = "total_transactions_count")
    private Integer totalTransactionsCount;

    @Column(name = "essential_transactions_count")
    private Integer essentialTransactionsCount;

    @Column(name = "discretionary_transactions_count")
    private Integer discretionaryTransactionsCount;

    @Column(name = "largest_spending_category_id")
    private Long largestSpendingCategoryId;

    @Column(name = "largest_spending_amount", precision = 15, scale = 2)
    private BigDecimal largestSpendingAmount;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public static ConsumerMonthlyAnalysis create(
            Long memberId,
            LocalDate analysisYearMonth,
            BigDecimal currentMonthSpending,
            BigDecimal sameDayAvgSpending,
            BigDecimal spendingDiffAmount,
            String spendingStatus,
            Integer totalTransactionsCount,
            Integer essentialTransactionsCount,
            Integer discretionaryTransactionsCount,
            Long largestSpendingCategoryId,
            BigDecimal largestSpendingAmount,
            OffsetDateTime now
    ) {
        ConsumerMonthlyAnalysis analysis = new ConsumerMonthlyAnalysis();
        analysis.memberId = memberId;
        analysis.analysisYearMonth = analysisYearMonth;
        analysis.currentMonthSpending = currentMonthSpending;
        analysis.sameDayAvgSpending = sameDayAvgSpending;
        analysis.spendingDiffAmount = spendingDiffAmount;
        analysis.spendingStatus = spendingStatus;
        analysis.totalTransactionsCount = totalTransactionsCount;
        analysis.essentialTransactionsCount = essentialTransactionsCount;
        analysis.discretionaryTransactionsCount = discretionaryTransactionsCount;
        analysis.largestSpendingCategoryId = largestSpendingCategoryId;
        analysis.largestSpendingAmount = largestSpendingAmount;
        analysis.createdAt = now;
        analysis.updatedAt = now;
        return analysis;
    }

    public void update(
            BigDecimal currentMonthSpending,
            BigDecimal sameDayAvgSpending,
            BigDecimal spendingDiffAmount,
            String spendingStatus,
            Integer totalTransactionsCount,
            Integer essentialTransactionsCount,
            Integer discretionaryTransactionsCount,
            Long largestSpendingCategoryId,
            BigDecimal largestSpendingAmount,
            OffsetDateTime now
    ) {
        this.currentMonthSpending = currentMonthSpending;
        this.sameDayAvgSpending = sameDayAvgSpending;
        this.spendingDiffAmount = spendingDiffAmount;
        this.spendingStatus = spendingStatus;
        this.totalTransactionsCount = totalTransactionsCount;
        this.essentialTransactionsCount = essentialTransactionsCount;
        this.discretionaryTransactionsCount = discretionaryTransactionsCount;
        this.largestSpendingCategoryId = largestSpendingCategoryId;
        this.largestSpendingAmount = largestSpendingAmount;
        this.updatedAt = now;
    }
}
