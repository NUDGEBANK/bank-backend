package com.nudgebank.bankbackend.loan.domain;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.card.domain.Card;
import com.nudgebank.bankbackend.credit.domain.CreditHistory;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "loan_amount", precision = 15, scale = 2)
    private BigDecimal loanAmount;

    @Column(name = "loan_term", length = 100)
    private String loanTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", length = 100, nullable = false)
    private LoanApplicationStatus applicationStatus;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "review_comment", length = 200)
    private String reviewComment;

    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "protected_balance", precision = 15, scale = 2)
    private BigDecimal protectedBalance;

    @Column(name = "salary_date")
    private Integer salaryDate;

    public boolean isPendingReview() {
        return this.applicationStatus == LoanApplicationStatus.PENDING;
    }

    public void approve() {
        if (!isPendingReview()) {
            throw new IllegalStateException("심사 대기 상태가 아닙니다. currentStatus=" + this.applicationStatus);
        }
        this.applicationStatus = LoanApplicationStatus.APPROVED;
    }

    public void reject(Long reviewerId) {
        if (!isPendingReview()) {
            throw new IllegalStateException("심사 대기 상태가 아닙니다. currentStatus=" + this.applicationStatus);
        }
        this.applicationStatus = LoanApplicationStatus.REJECTED;
    }
}
