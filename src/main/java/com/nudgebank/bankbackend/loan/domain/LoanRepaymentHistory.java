package com.nudgebank.bankbackend.loan.domain;

import com.nudgebank.bankbackend.card.domain.CardTransaction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "loan_repayment_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Access(AccessType.FIELD)
public class LoanRepaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repayment_id")
    private Long repaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_history_id", nullable = false)
    private LoanHistory loanHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private CardTransaction transaction;

    @Column(name = "repayment_amount", precision = 15, scale = 2)
    private BigDecimal repaymentAmount;

    @Column(name = "repayment_rate", precision = 5, scale = 2)
    private BigDecimal repaymentRate;

    @Column(name = "repayment_datetime")
    private OffsetDateTime repaymentDatetime;

    @Column(name = "remaining_balance", precision = 15, scale = 2)
    private BigDecimal remainingBalance;
}
