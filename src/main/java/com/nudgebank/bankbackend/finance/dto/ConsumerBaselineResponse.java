package com.nudgebank.bankbackend.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ConsumerBaselineResponse(
        Long baselineId,
        LocalDate analysisYearMonth,
        BigDecimal avgSpending,
        BigDecimal essentialRatio,
        BigDecimal normalRatio,
        BigDecimal discretionaryRatio,
        BigDecimal riskRatio,
        BigDecimal volatility,
        BigDecimal volatilityIndex,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
