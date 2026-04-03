package com.nudgebank.bankbackend.loan.domain;

import com.nudgebank.bankbackend.auth.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "loan_application")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loan_application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_product_id", nullable = false)
    private LoanProduct loanProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_history_id", nullable = false)
    private CreditHistory creditHistory;

    @Column(name = "loan_amount", precision = 15, scale = 2)
    private BigDecimal loanAmount;

    @Column(name = "loan_term", length = 100)
    private String loanTerm;

    @Column(name = "application_status", length = 100)
    private String applicationStatus;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "review_comment", length = 200)
    private String reviewComment;

    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "salary_date")
    private Integer salaryDate;
}