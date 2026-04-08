package com.nudgebank.bankbackend.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AutoRepaymentDecisionResponse {
    private Long memberId;
    private Long transactionId;

    private BigDecimal baseRepaymentRatio;
    private BigDecimal finalRepaymentRatio;
    private String repaymentAction;
    private String policyGrade;

    private BigDecimal availableBalance;
    private BigDecimal monthlyIncome;
    private BigDecimal currentMonthSpendingAmount;
    private BigDecimal totalLoanRemainingPrincipal;
    private Integer daysUntilPaymentDue;

    private BigDecimal essentialRatio;
    private BigDecimal discretionaryRatio;
    private BigDecimal riskRatio;
    private BigDecimal volatility;

    private String baselineSource;
    private String policyReason;
}
