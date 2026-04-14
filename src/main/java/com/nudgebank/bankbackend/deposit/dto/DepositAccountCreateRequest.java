package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;

public record DepositAccountCreateRequest(
    Long depositProductId,
    Long accountId,
    BigDecimal joinAmount,
    Integer savingMonth,
    BigDecimal monthlyPaymentAmount,
    Boolean autoTransferYn,
    Integer autoTransferDay
) {
    public DepositAccountCreateRequest {
        if (depositProductId == null || depositProductId <= 0) {
            throw new IllegalArgumentException("depositProductId는 1 이상이어야 합니다.");
        }
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("accountId는 1 이상이어야 합니다.");
        }
        if (joinAmount == null || joinAmount.signum() <= 0) {
            throw new IllegalArgumentException("joinAmount는 0보다 커야 합니다.");
        }
        if (savingMonth == null || savingMonth <= 0) {
            throw new IllegalArgumentException("savingMonth는 1 이상이어야 합니다.");
        }
        if (monthlyPaymentAmount != null && monthlyPaymentAmount.signum() <= 0) {
            throw new IllegalArgumentException("monthlyPaymentAmount는 0보다 커야 합니다.");
        }
        if (Boolean.TRUE.equals(autoTransferYn)) {
            if (autoTransferDay == null || autoTransferDay < 1 || autoTransferDay > 31) {
                throw new IllegalArgumentException("autoTransferDay는 1~31 사이여야 합니다.");
            }
        } else if (autoTransferDay != null) {
            throw new IllegalArgumentException("autoTransferYn이 false/null이면 autoTransferDay는 비워야 합니다.");
        }
    }
}
