package com.nudgebank.bankbackend.loan.domain;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "repayment_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Access(AccessType.FIELD)
public class RepaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_history_id", nullable = false)
    private LoanHistory loanHistory;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "planned_principal", precision = 15, scale = 2)
    private BigDecimal plannedPrincipal;

    @Column(name = "planned_interest", precision = 15, scale = 2)
    private BigDecimal plannedInterest;

    @Column(name = "paid_principal", precision = 15, scale = 2)
    private BigDecimal paidPrincipal;

    @Column(name = "paid_interest", precision = 15, scale = 2)
    private BigDecimal paidInterest;

    @Column(name = "is_settled")
    private Boolean isSettled;

    @Column(name = "overdue_days")
    private Integer overdueDays;

    @Column(name = "\"Key\"")
    private String recordKey;
}
