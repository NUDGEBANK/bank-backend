package com.nudgebank.bankbackend.loan.domain;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.card.domain.Card;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "loan_history")
public class LoanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loan_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "total_principal", precision = 15, scale = 2)
    private BigDecimal totalPrincipal;

    @Column(name = "repayment_account_number", length = 200)
    private String repaymentAccountNumber;

    @Column(name = "remaining_principal", precision = 15, scale = 2)
    private BigDecimal remainingPrincipal;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", length = 100)
    private String status;

    @Column(name = "expected_repayment_date")
    private LocalDate expectedRepaymentDate;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public static LoanHistory create(
        Member member,
        Card card,
        BigDecimal totalPrincipal,
        String repaymentAccountNumber,
        BigDecimal remainingPrincipal,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        LocalDate expectedRepaymentDate,
        OffsetDateTime createdAt
    ) {
        return LoanHistory.builder()
            .member(member)
            .card(card)
            .totalPrincipal(totalPrincipal)
            .repaymentAccountNumber(repaymentAccountNumber)
            .remainingPrincipal(remainingPrincipal)
            .startDate(startDate)
            .endDate(endDate)
            .status(status)
            .expectedRepaymentDate(expectedRepaymentDate)
            .createdAt(createdAt)
            .build();
    }

    public BigDecimal applyRepayment(BigDecimal repaymentAmount) {
        if (repaymentAmount == null || repaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("상환 금액은 0보다 커야 합니다.");
        }

        BigDecimal currentRemainingPrincipal = this.remainingPrincipal != null
                ? this.remainingPrincipal
                : BigDecimal.ZERO;

        BigDecimal appliedAmount = repaymentAmount.min(currentRemainingPrincipal);
        this.remainingPrincipal = currentRemainingPrincipal.subtract(appliedAmount);

        if (this.remainingPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            this.remainingPrincipal = BigDecimal.ZERO;
            this.status = "COMPLETED";
            this.endDate = LocalDate.now();
        }

        return appliedAmount;
    }

    public void syncRepaymentStatus(LocalDate nextRepaymentDate, boolean overdue) {
        this.expectedRepaymentDate = nextRepaymentDate;

        if (this.remainingPrincipal != null && this.remainingPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            this.remainingPrincipal = BigDecimal.ZERO;
            this.status = "COMPLETED";
            this.endDate = LocalDate.now();
            this.expectedRepaymentDate = null;
            return;
        }

        this.status = overdue ? "OVERDUE" : "ACTIVE";
    }
}
