package com.nudgebank.bankbackend.loan.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "loan_product")
public class LoanProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loan_product_id")
    private Long id;

    @Column(name = "loan_product_name", length = 200)
    private String loanProductName;

    @Column(name = "loan_product_description", columnDefinition = "TEXT")
    private String loanProductDescription;

    @Column(name = "min_interest_rate", precision = 5, scale = 2)
    private BigDecimal minInterestRate;

    @Column(name = "max_interest_rate", precision = 5, scale = 2)
    private BigDecimal maxInterestRate;

    @Column(name = "max_limit_amount")
    private Long maxLimitAmount;

    @Column(name = "min_limit_amount")
    private Long minLimitAmount;

    @Column(name = "repayment_period_month")
    private Integer repaymentPeriodMonth;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "loan_product_type", length = 50)
    private String loanProductType;

    @Column(name = "repayment_type", length = 50)
    private String repaymentType;

    @OneToMany(mappedBy = "loanProduct")
    @Builder.Default
    private List<LoanApplication> loanApplications = new ArrayList<>();

    public void updateProduct(
        String loanProductName,
        String loanProductDescription,
        BigDecimal minInterestRate,
        BigDecimal maxInterestRate,
        Long maxLimitAmount,
        Long minLimitAmount,
        Integer repaymentPeriodMonth,
        String repaymentType
    ) {
        this.loanProductName = loanProductName;
        this.loanProductDescription = loanProductDescription;
        this.minInterestRate = minInterestRate;
        this.maxInterestRate = maxInterestRate;
        this.maxLimitAmount = maxLimitAmount;
        this.minLimitAmount = minLimitAmount;
        this.repaymentPeriodMonth = repaymentPeriodMonth;
        this.repaymentType = repaymentType;
    }
}
