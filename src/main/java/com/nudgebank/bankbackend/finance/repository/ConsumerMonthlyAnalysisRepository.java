package com.nudgebank.bankbackend.finance.repository;

import com.nudgebank.bankbackend.finance.domain.ConsumerMonthlyAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConsumerMonthlyAnalysisRepository extends JpaRepository<ConsumerMonthlyAnalysis, Long> {
    List<ConsumerMonthlyAnalysis> findAllByMemberIdOrderByAnalysisYearMonthAsc(Long memberId);
    Optional<ConsumerMonthlyAnalysis> findByMemberIdAndAnalysisYearMonth(Long memberId, LocalDate analysisYearMonth);
}
