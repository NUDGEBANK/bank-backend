package com.nudgebank.bankbackend.deposit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "deposit_product_rate")
public class DepositProductRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deposit_product_rate_id")
    private Long depositProductRateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_product_id", nullable = false)
    private DepositProduct depositProduct;

    @Column(name = "min_saving_month", nullable = false)
    private Integer minSavingMonth;

    @Column(name = "max_saving_month", nullable = false)
    private Integer maxSavingMonth;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Builder
    private DepositProductRate(
        Long depositProductRateId,
        DepositProduct depositProduct,
        Integer minSavingMonth,
        Integer maxSavingMonth,
        BigDecimal interestRate
    ) {
        Objects.requireNonNull(depositProduct, "depositProduct는 필수입니다.");
        if (minSavingMonth == null || minSavingMonth <= 0) {
            throw new IllegalArgumentException("minSavingMonth는 1 이상이어야 합니다.");
        }
        if (maxSavingMonth == null || maxSavingMonth < minSavingMonth) {
            throw new IllegalArgumentException("maxSavingMonth는 minSavingMonth 이상이어야 합니다.");
        }
        validateInterestRate(interestRate);

        this.depositProductRateId = depositProductRateId;
        this.depositProduct = depositProduct;
        this.minSavingMonth = minSavingMonth;
        this.maxSavingMonth = maxSavingMonth;
        this.interestRate = interestRate;
    }

    public void update(BigDecimal interestRate) {
        validateInterestRate(interestRate);
        this.interestRate = interestRate;
    }

    private void validateInterestRate(BigDecimal interestRate) {
        if (interestRate == null || interestRate.signum() < 0) {
            throw new IllegalArgumentException("interestRate는 0 이상이어야 합니다.");
        }
    }
}
