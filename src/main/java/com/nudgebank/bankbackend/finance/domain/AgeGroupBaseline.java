package com.nudgebank.bankbackend.finance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "age_group_baseline")
@Getter
@NoArgsConstructor
public class AgeGroupBaseline {

    @Id
    @Column(name = "age_group", nullable = false, length = 20)
    private String ageGroup;

    @Column(name = "avg_spending", precision = 15, scale = 2)
    private BigDecimal avgSpending;

    @Column(name = "essential_ratio", precision = 5, scale = 4)
    private BigDecimal essentialRatio;

    @Column(name = "normal_ratio", precision = 5, scale = 4)
    private BigDecimal normalRatio;

    @Column(name = "discretionary_ratio", precision = 5, scale = 4)
    private BigDecimal discretionaryRatio;

    @Column(name = "risk_ratio", precision = 5, scale = 4)
    private BigDecimal riskRatio;

    @Column(name = "volatility", precision = 15, scale = 2)
    private BigDecimal volatility;

    @Column(name = "volatility_index", precision = 5, scale = 4)
    private BigDecimal volatilityIndex;

    @Column(name = "repayment_action", length = 20)
    private String repaymentAction;

    public AgeGroupBaseline(
            String ageGroup,
            BigDecimal avgSpending,
            BigDecimal essentialRatio,
            BigDecimal normalRatio,
            BigDecimal discretionaryRatio,
            BigDecimal riskRatio,
            BigDecimal volatility,
            BigDecimal volatilityIndex,
            String repaymentAction
    ) {
        this.ageGroup = ageGroup;
        this.avgSpending = avgSpending;
        this.essentialRatio = essentialRatio;
        this.normalRatio = normalRatio;
        this.discretionaryRatio = discretionaryRatio;
        this.riskRatio = riskRatio;
        this.volatility = volatility;
        this.volatilityIndex = volatilityIndex;
        this.repaymentAction = repaymentAction;
    }
}
