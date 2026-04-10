package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;
import java.util.List;

public record LoanEligibilityResponse(
        boolean eligible,
        String decision,
        Integer creditScore,
        Long estimatedLimit,
        String productKey,
        BigDecimal requestedAmount,
        List<String> reasons
) {}
