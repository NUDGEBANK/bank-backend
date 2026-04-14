package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;

public record DepositProductRateResponse(
    Long depositProductRateId,
    Integer minSavingMonth,
    Integer maxSavingMonth,
    BigDecimal interestRate
) {
}
