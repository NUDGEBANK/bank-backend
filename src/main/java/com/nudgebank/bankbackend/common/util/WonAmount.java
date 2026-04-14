package com.nudgebank.bankbackend.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class WonAmount {

    private WonAmount() {
    }

    public static BigDecimal floor(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(0, RoundingMode.DOWN);
    }
}
