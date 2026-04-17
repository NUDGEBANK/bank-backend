package com.nudgebank.bankbackend.loan.dto;

import java.util.List;

/**
 * 내부 신용점수와 상품 조건을 기준으로 산정한
 * 대출 가능 여부 조회 응답 DTO.
 */
public record LoanEligibilityResponse(
        boolean eligible,          // 최종 대출 가능 여부: true/false
        String decision,           // 내부 판단 결과: APPROVED / REJECTED
        Integer creditScore,       // 조회 시점의 최신 내부 신용점수
        String productKey,         // 조회 대상 상품 키: youth-loan, consumption-loan
        List<String> reasons       // 가능/불가 판단 사유 목록
) {}
