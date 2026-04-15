package com.nudgebank.bankbackend.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ConsumerPredictionResponse(
        Long predictionId,
        LocalDate analysisYearMonth,
        LocalDate predictedYearMonth,
        BigDecimal predictedTotalSpending,
        String modelVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
