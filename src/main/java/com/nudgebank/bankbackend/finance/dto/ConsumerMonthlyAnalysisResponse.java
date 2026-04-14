package com.nudgebank.bankbackend.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConsumerMonthlyAnalysisResponse(
        Long analysisId,
        LocalDate analysisYearMonth,
        BigDecimal currentMonthSpending,
        BigDecimal sameDayAvgSpending,
        BigDecimal spendingDiffAmount,
        String spendingStatus,
        Integer totalTransactionsCount,
        Integer essentialTransactionsCount,
        Integer discretionaryTransactionsCount,
        Long largestSpendingCategoryId,
        BigDecimal largestSpendingAmount
) {
}
