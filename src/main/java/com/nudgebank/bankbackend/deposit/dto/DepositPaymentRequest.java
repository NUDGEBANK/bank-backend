package com.nudgebank.bankbackend.deposit.dto;

import java.math.BigDecimal;

public record DepositPaymentRequest(
    BigDecimal amount
) {
}
