package com.nudgebank.bankbackend.loan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class ConsumptionAnalysisInterestPolicy {

    static final int MIN_ELIGIBLE_CREDIT_SCORE = 500;
    static final int BEST_RATE_CREDIT_SCORE = 850;
    static final BigDecimal MIN_INTEREST_RATE = new BigDecimal("5.00");
    static final BigDecimal MAX_INTEREST_RATE = new BigDecimal("12.00");
    private static final BigDecimal SCORE_INTERVAL = BigDecimal.valueOf(BEST_RATE_CREDIT_SCORE - MIN_ELIGIBLE_CREDIT_SCORE);
    private static final BigDecimal RATE_INTERVAL = MAX_INTEREST_RATE.subtract(MIN_INTEREST_RATE);

    private ConsumptionAnalysisInterestPolicy() {
    }

    static BigDecimal resolve(int creditScore) {
        if (creditScore <= MIN_ELIGIBLE_CREDIT_SCORE) {
            return MAX_INTEREST_RATE;
        }
        if (creditScore >= BEST_RATE_CREDIT_SCORE) {
            return MIN_INTEREST_RATE;
        }

        BigDecimal scoreOffset = BigDecimal.valueOf(creditScore - MIN_ELIGIBLE_CREDIT_SCORE);
        BigDecimal discount = scoreOffset
            .multiply(RATE_INTERVAL)
            .divide(SCORE_INTERVAL, 10, RoundingMode.HALF_UP);

        return MAX_INTEREST_RATE.subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }
}
