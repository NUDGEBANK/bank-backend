package com.nudgebank.bankbackend.finance.dto;

import java.util.List;

public record ConsumptionAnalysisOverviewResponse(
        List<ConsumerMonthlyAnalysisResponse> monthlyAnalyses, // 월별 소비 분석 리스트
        ConsumerPredictionResponse latestPrediction               // 최신 소비 예측 결과
) {
}
