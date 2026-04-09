package com.nudgebank.bankbackend.card.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CardPaymentResponse {

    private Long transactionId;
    // 카드 결제 거래 ID
    private BigDecimal paymentAmount;
    // 결제 금액
    private Boolean autoRepaymentApplied;
    // 결제 직후 자동상환이 실제 적용되었는지 여부
    private String repaymentAction;
    // 자동상환 정책 엔진이 결정한 액션 (예: HOLD, STANDARD)
    private String policyGrade;
    // 자동상환 정책 등급 (예: NO_LOAN, BALANCED)
    private BigDecimal repaymentRatio;
    // 실제 적용된 자동상환 비율 원값 (예: 0.20)
    private BigDecimal repaymentAmount;
    // 결제 직후 추가로 차감된 자동상환 금액
    private BigDecimal remainingLoanBalance;
    // 자동상환 반영 후 남은 대출 원금
    private BigDecimal totalDebitedAmount;
    // 결제금액 + 자동상환금액을 합한 총 차감 금액
}
