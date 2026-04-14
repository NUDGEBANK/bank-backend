package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;
import java.util.List;

public record DepositProductResponse(
    Long depositProductId,
    String depositProductName,
    String depositProductType,
    String depositProductDescription,
    BigDecimal depositMinAmount,
    BigDecimal depositMaxAmount,
    Integer minSavingMonth,
    Integer maxSavingMonth,
    List<DepositProductRateResponse> rates
) {
}
