package com.nudgebank.bankbackend.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class FinalBaselineResponse {
    private Long memberId;
    private String ageGroup;
    private Integer age;

    private BigDecimal ageBaselineWeight;
    private BigDecimal personalBaselineWeight;

    private LocalDate baselineStartDate;
    private LocalDate baselineEndDate;
    private Integer usageDays;

    private BigDecimal avgSpending;
    private BigDecimal essentialRatio;
    private BigDecimal normalRatio;
    private BigDecimal discretionaryRatio;
    private BigDecimal riskRatio;
    private BigDecimal volatility;

    private String baselineSource; // AGE_ONLY, MIXED, PERSONAL_HEAVY
}