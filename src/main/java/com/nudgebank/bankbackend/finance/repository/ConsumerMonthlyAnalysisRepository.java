package com.nudgebank.bankbackend.finance.repository;

import com.nudgebank.bankbackend.finance.domain.ConsumerMonthlyAnalysis;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ConsumerMonthlyAnalysisRepository extends JpaRepository<ConsumerMonthlyAnalysis, Long> {
    List<ConsumerMonthlyAnalysis> findAllByMemberIdOrderByAnalysisYearMonthAsc(Long memberId);
    Optional<ConsumerMonthlyAnalysis> findByMemberIdAndAnalysisYearMonth(Long memberId, LocalDate analysisYearMonth);

    @Modifying
    @Query(value = """
            insert into consumer_monthly_analysis (
                member_id,
                analysis_year_month,
                current_month_spending,
                same_day_avg_spending,
                spending_diff_amount,
                spending_status,
                total_transactions_count,
                essential_transactions_count,
                discretionary_transactions_count,
                largest_spending_category_id,
                largest_spending_amount,
                created_at,
                updated_at
            ) values (
                :memberId,
                :analysisYearMonth,
                :currentMonthSpending,
                :sameDayAvgSpending,
                :spendingDiffAmount,
                :spendingStatus,
                :totalTransactionsCount,
                :essentialTransactionsCount,
                :discretionaryTransactionsCount,
                :largestSpendingCategoryId,
                :largestSpendingAmount,
                :now,
                :now
            )
            on conflict (member_id, analysis_year_month) do update
            set current_month_spending = excluded.current_month_spending,
                same_day_avg_spending = excluded.same_day_avg_spending,
                spending_diff_amount = excluded.spending_diff_amount,
                spending_status = excluded.spending_status,
                total_transactions_count = excluded.total_transactions_count,
                essential_transactions_count = excluded.essential_transactions_count,
                discretionary_transactions_count = excluded.discretionary_transactions_count,
                largest_spending_category_id = excluded.largest_spending_category_id,
                largest_spending_amount = excluded.largest_spending_amount,
                updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(
            @Param("memberId") Long memberId,
            @Param("analysisYearMonth") LocalDate analysisYearMonth,
            @Param("currentMonthSpending") BigDecimal currentMonthSpending,
            @Param("sameDayAvgSpending") BigDecimal sameDayAvgSpending,
            @Param("spendingDiffAmount") BigDecimal spendingDiffAmount,
            @Param("spendingStatus") String spendingStatus,
            @Param("totalTransactionsCount") Integer totalTransactionsCount,
            @Param("essentialTransactionsCount") Integer essentialTransactionsCount,
            @Param("discretionaryTransactionsCount") Integer discretionaryTransactionsCount,
            @Param("largestSpendingCategoryId") Long largestSpendingCategoryId,
            @Param("largestSpendingAmount") BigDecimal largestSpendingAmount,
            @Param("now") OffsetDateTime now
    );
}
