package com.nudgebank.bankbackend.finance.repository;

import com.nudgebank.bankbackend.finance.domain.ConsumerPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsumerPredictionRepository extends JpaRepository<ConsumerPrediction, Long> {
    Optional<ConsumerPrediction> findTopByMemberIdOrderByAnalysisYearMonthDesc(Long memberId);
}