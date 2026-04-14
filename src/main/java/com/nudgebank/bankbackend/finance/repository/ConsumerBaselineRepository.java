package com.nudgebank.bankbackend.finance.repository;

import com.nudgebank.bankbackend.finance.domain.ConsumerBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ConsumerBaselineRepository extends JpaRepository<ConsumerBaseline, Long> {
    Optional<ConsumerBaseline> findByMemberIdAndAnalysisYearMonth(Long memberId, LocalDate analysisYearMonth);

    Optional<ConsumerBaseline> findTopByMemberIdOrderByAnalysisYearMonthDesc(Long memberId);
}
