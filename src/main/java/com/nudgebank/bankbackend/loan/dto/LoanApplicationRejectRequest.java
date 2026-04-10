package com.nudgebank.bankbackend.loan.dto;

public record LoanApplicationRejectRequest(
        String reason // 거절 사유
) {}
