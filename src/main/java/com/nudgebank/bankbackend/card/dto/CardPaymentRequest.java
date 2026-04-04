package com.nudgebank.bankbackend.card.dto;

import java.math.BigDecimal;

public record CardPaymentRequest(
        Long cardId,
        Long marketId,
        Long categoryId,
        String qrId,
        BigDecimal amount,
        String menuName,
        Integer quantity
) {
}