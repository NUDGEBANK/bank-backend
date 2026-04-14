package com.nudgebank.bankbackend.finance.repository;

import com.nudgebank.bankbackend.finance.domain.ConsumerPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface ConsumerPredictionRepository extends JpaRepository<ConsumerPrediction, Long> {
    Optional<ConsumerPrediction> findTopByMemberIdOrderByAnalysisYearMonthDesc(Long memberId);
    Optional<ConsumerPrediction> findByMemberIdAndAnalysisYearMonthAndUpdatedAtGreaterThanEqual(
            Long memberId,
            LocalDate analysisYearMonth,
            OffsetDateTime updatedAt
    );
}
