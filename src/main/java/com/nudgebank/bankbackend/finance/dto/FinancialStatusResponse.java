package com.nudgebank.bankbackend.finance.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class FinancialStatusResponse {

    private Long memberId;
    private Long cardId;
    private Long accountId;

    // 자산/현금흐름
    private BigDecimal linkedAccountBalance; //연동 계좌 잔액
    private BigDecimal availableBalance; //사용 가능한 잔액
    private BigDecimal monthlyIncome;    //월 소득
    private Integer salaryDate;          //급여일

    // 대출 상태
    private BigDecimal totalLoanRemainingPrincipal; //총 대출 잔액
    private BigDecimal monthlyRepaidAmount;        //이번 달 누적 상환 금액
    private BigDecimal monthlyRemainingRepaymentAmount; //이번 달 남은 상환 금액
    private Integer daysUntilPaymentDue;           //상환 예정일까지 남은 일 수

    // 소비 상태
    private BigDecimal currentMonthSpendingAmount; //이번 달 누적 지출 금액
}
