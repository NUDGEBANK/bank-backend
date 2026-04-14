package com.nudgebank.bankbackend.finance.repository;

import com.nudgebank.bankbackend.finance.domain.ConsumerBaseline;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface ConsumerBaselineRepository extends JpaRepository<ConsumerBaseline, Long> {
    Optional<ConsumerBaseline> findByMemberIdAndAnalysisYearMonth(Long memberId, LocalDate analysisYearMonth);

    Optional<ConsumerBaseline> findTopByMemberIdOrderByAnalysisYearMonthDesc(Long memberId);

    @Modifying
    @Query(value = """
            insert into consumer_baseline (
                member_id,
                avg_spending,
                essential_ratio,
                normal_ratio,
                discretionary_ratio,
                risk_ratio,
                volatility,
                volatility_index,
                analysis_year_month,
                created_at,
                updated_at
            ) values (
                :memberId,
                :avgSpending,
                :essentialRatio,
                :normalRatio,
                :discretionaryRatio,
                :riskRatio,
                :volatility,
                :volatilityIndex,
                :analysisYearMonth,
                :now,
                :now
            )
            on conflict (member_id, analysis_year_month) do update
            set avg_spending = excluded.avg_spending,
                essential_ratio = excluded.essential_ratio,
                normal_ratio = excluded.normal_ratio,
                discretionary_ratio = excluded.discretionary_ratio,
                risk_ratio = excluded.risk_ratio,
                volatility = excluded.volatility,
                volatility_index = excluded.volatility_index,
                updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(
            @Param("memberId") Long memberId,
            @Param("avgSpending") BigDecimal avgSpending,
            @Param("essentialRatio") BigDecimal essentialRatio,
            @Param("normalRatio") BigDecimal normalRatio,
            @Param("discretionaryRatio") BigDecimal discretionaryRatio,
            @Param("riskRatio") BigDecimal riskRatio,
            @Param("volatility") BigDecimal volatility,
            @Param("volatilityIndex") BigDecimal volatilityIndex,
            @Param("analysisYearMonth") LocalDate analysisYearMonth,
            @Param("now") OffsetDateTime now
    );
}
