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

    public static RepaymentSchedule create(
        LoanHistory loanHistory,
        LocalDate dueDate,
        BigDecimal plannedPrincipal,
        BigDecimal plannedInterest
    ) {
        RepaymentSchedule schedule = new RepaymentSchedule();
        schedule.loanHistory = loanHistory;
        schedule.dueDate = dueDate;
        schedule.plannedPrincipal = plannedPrincipal;
        schedule.plannedInterest = plannedInterest;
        schedule.paidPrincipal = BigDecimal.ZERO;
        schedule.paidInterest = BigDecimal.ZERO;
        schedule.isSettled = false;
        schedule.overdueDays = 0;
        return schedule;
    }

    public void updatePlannedInterest(BigDecimal plannedInterest) {
        this.plannedInterest = plannedInterest;
    }

    public void updatePlannedAmounts(BigDecimal plannedPrincipal, BigDecimal plannedInterest) {
        this.plannedPrincipal = plannedPrincipal;
        this.plannedInterest = plannedInterest;
    }

    public BigDecimal getRemainingPlannedPrincipal() {
        return nullSafe(plannedPrincipal).subtract(nullSafe(paidPrincipal)).max(BigDecimal.ZERO);
    }

    public BigDecimal getRemainingPlannedInterest() {
        return nullSafe(plannedInterest).subtract(nullSafe(paidInterest)).max(BigDecimal.ZERO);
    }

    public void addPaidPrincipal(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        this.paidPrincipal = nullSafe(this.paidPrincipal).add(amount);
    }

    public void addPaidInterest(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        this.paidInterest = nullSafe(this.paidInterest).add(amount);
    }

    public boolean normalizePaidAmounts() {
        BigDecimal currentPlannedInterest = nullSafe(plannedInterest);
        BigDecimal currentPaidInterest = nullSafe(paidInterest);
        if (currentPaidInterest.compareTo(currentPlannedInterest) <= 0) {
            return false;
        }

        BigDecimal excessInterest = currentPaidInterest.subtract(currentPlannedInterest);
        this.paidInterest = currentPlannedInterest;
        this.paidPrincipal = nullSafe(this.paidPrincipal).add(excessInterest);
        return true;
    }

    public void markSettled() {
        this.isSettled = true;
        this.overdueDays = 0;
    }

    public void markPending(Integer overdueDays) {
        this.isSettled = false;
        this.overdueDays = overdueDays != null ? Math.max(overdueDays, 0) : 0;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

}
