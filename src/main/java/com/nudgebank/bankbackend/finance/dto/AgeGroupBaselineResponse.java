package com.nudgebank.bankbackend.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AgeGroupBaselineResponse {
    private Long memberId;                  // 회원 ID
    private String ageGroup;                // 연령대 (예: 20s, 30s, 60s+)
    private Integer age;                    // 현재 사용자 나이
    private BigDecimal avgSpending;         // 평균 소비 금액
    private BigDecimal essentialRatio;      // 필수 소비 비율
    private BigDecimal normalRatio;         // 일반 소비 비율
    private BigDecimal discretionaryRatio;  // 재량 소비 비율
    private BigDecimal riskRatio;           // 위험 소비 비율
    private BigDecimal volatility;          // 소비 변동성
    private BigDecimal volatilityIndex;     // 평균 대비 상대 변동성 지수
    private String repaymentAction;         // 추천 상환 전략
}