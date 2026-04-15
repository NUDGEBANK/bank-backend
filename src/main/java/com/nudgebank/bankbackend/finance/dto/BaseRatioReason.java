package com.nudgebank.bankbackend.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BaseRatioReason {
    BigDecimal baseRatio;
    List<String> reasons;
}
