package com.nudgebank.bankbackend.finance.dto;

import com.nudgebank.bankbackend.card.domain.CardTransaction;
import lombok.Getter;

@Getter
public class AutoRepaymentRequest {
    Long memberId;
    Long cardTransaction;
}
