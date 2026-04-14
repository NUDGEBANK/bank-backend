package com.nudgebank.bankbackend.deposit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "deposit_product", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(name = "uk_deposit_product_type", columnNames = "deposit_product_type")
})
public class DepositProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deposit_product_id")
    private Long depositProductId;

    @Column(name = "deposit_product_name", nullable = false, length = 100)
    private String depositProductName;

    @Column(name = "deposit_product_type", nullable = false, length = 30)
    private String depositProductType;

    @Column(name = "deposit_product_description", length = 255)
    private String depositProductDescription;

    @Column(name = "deposit_min_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal depositMinAmount;

    @Column(name = "deposit_max_amount", precision = 15, scale = 2)
    private BigDecimal depositMaxAmount;

    @Column(name = "min_saving_month", nullable = false)
    private Integer minSavingMonth;

    @Column(name = "max_saving_month", nullable = false)
    private Integer maxSavingMonth;

    @Builder
    private DepositProduct(
        Long depositProductId,
        String depositProductName,
        String depositProductType,
        String depositProductDescription,
        BigDecimal depositMinAmount,
        BigDecimal depositMaxAmount,
        Integer minSavingMonth,
        Integer maxSavingMonth
    ) {
        this.depositProductId = depositProductId;
        this.depositProductName = depositProductName;
        this.depositProductType = depositProductType;
        this.depositProductDescription = depositProductDescription;
        this.depositMinAmount = depositMinAmount;
        this.depositMaxAmount = depositMaxAmount;
        this.minSavingMonth = minSavingMonth;
        this.maxSavingMonth = maxSavingMonth;
    }

    public void update(
        String depositProductName,
        String depositProductDescription,
        BigDecimal depositMinAmount,
        BigDecimal depositMaxAmount,
        Integer minSavingMonth,
        Integer maxSavingMonth
    ) {
        this.depositProductName = depositProductName;
        this.depositProductDescription = depositProductDescription;
        this.depositMinAmount = depositMinAmount;
        this.depositMaxAmount = depositMaxAmount;
        this.minSavingMonth = minSavingMonth;
        this.maxSavingMonth = maxSavingMonth;
    }
}
