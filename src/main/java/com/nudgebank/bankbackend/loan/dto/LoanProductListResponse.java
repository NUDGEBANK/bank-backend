package com.nudgebank.bankbackend.loan.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 대출 상품 목록 응답
@Getter
@AllArgsConstructor
public class LoanProductListResponse {
    private Long loanProductId; // 대출 상품 ID
    private String loanProductName; // 대출 상품명
    private String loanProductDescription; // 대출 상품 설명
    private Long minLimitAmount; // 최소 한도 금액 (원)
    private Long maxLimitAmount; // 최대 한도 금액 (원)
    private Integer repaymentPeriodMonth; // 상환 기간 (개월)
    private BigDecimal minInterestRate; // 최소 금리 (%)
    private BigDecimal maxInterestRate; // 최대 금리 (%)
    private String targetCustomer; // 대상
    private String loanProductType; // 대출 유형
    private String repaymentType; // 상환 방식
}
