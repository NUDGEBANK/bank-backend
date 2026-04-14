package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.finance.domain.ConsumerMonthlyAnalysis;
import com.nudgebank.bankbackend.finance.domain.ConsumerPrediction;
import com.nudgebank.bankbackend.finance.dto.ConsumerMonthlyAnalysisResponse;
import com.nudgebank.bankbackend.finance.dto.ConsumerPredictionResponse;
import com.nudgebank.bankbackend.finance.dto.ConsumptionAnalysisOverviewResponse;
import com.nudgebank.bankbackend.finance.repository.ConsumerMonthlyAnalysisRepository;
import com.nudgebank.bankbackend.finance.repository.ConsumerPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsumptionAnalysisQueryService {

    private final ConsumerMonthlyAnalysisRepository consumerMonthlyAnalysisRepository;
    private final ConsumerPredictionRepository consumerPredictionRepository;

    public List<ConsumerMonthlyAnalysisResponse> getMonthlyAnalyses(Long memberId) {
        return consumerMonthlyAnalysisRepository.findAllByMemberIdOrderByAnalysisYearMonthAsc(memberId).stream()
                .map(this::toMonthlyAnalysisItemResponse)
                .toList();
    }

    public ConsumerPredictionResponse getLatestPrediction(Long memberId) {
        return consumerPredictionRepository.findTopByMemberIdOrderByAnalysisYearMonthDesc(memberId)
                .map(this::toPredictionResponse)
                .orElse(null);
    }

    public ConsumerPredictionResponse getPredictionForAnalysisMonthUpdatedAfter(
            Long memberId,
            LocalDate analysisYearMonth,
            OffsetDateTime startedAt
    ) {
        return consumerPredictionRepository
                .findByMemberIdAndAnalysisYearMonthAndUpdatedAtGreaterThanEqual(memberId, analysisYearMonth, startedAt)
                .map(this::toPredictionResponse)
                .orElseThrow(() -> new IllegalStateException("이번 실행 결과에 해당하는 소비 예측 데이터를 찾지 못했습니다."));
    }

    public ConsumptionAnalysisOverviewResponse getOverview(Long memberId) {
        return new ConsumptionAnalysisOverviewResponse(
                getMonthlyAnalyses(memberId),
                getLatestPrediction(memberId)
        );
    }

    private ConsumerMonthlyAnalysisResponse toMonthlyAnalysisItemResponse(ConsumerMonthlyAnalysis analysis) {
        return new ConsumerMonthlyAnalysisResponse(
                analysis.getAnalysisId(),
                analysis.getAnalysisYearMonth(),
                analysis.getCurrentMonthSpending(),
                analysis.getSameDayAvgSpending(),
                analysis.getSpendingDiffAmount(),
                analysis.getSpendingStatus(),
                analysis.getTotalTransactionsCount(),
                analysis.getEssentialTransactionsCount(),
                analysis.getDiscretionaryTransactionsCount(),
                analysis.getLargestSpendingCategoryId(),
                analysis.getLargestSpendingAmount()
        );
    }

    private ConsumerPredictionResponse toPredictionResponse(ConsumerPrediction prediction) {
        return new ConsumerPredictionResponse(
                prediction.getPredictionId(),
                prediction.getAnalysisYearMonth(),
                prediction.getPredictedYearMonth(),
                prediction.getPredictedTotalSpending(),
                prediction.getModelVersion(),
                prediction.getCreatedAt(),
                prediction.getUpdatedAt()
        );
    }
}
