package com.nudgebank.bankbackend.deposit.domain;

import com.nudgebank.bankbackend.account.domain.Account;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "deposit_payment_schedule")
public class DepositPaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deposit_payment_schedule_id")
    private Long depositPaymentScheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_account_id", nullable = false)
    private DepositAccount depositAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "installment_no", nullable = false)
    private Integer installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "planned_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal plannedAmount;

    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid;

    @Column(name = "auto_transfer_yn", nullable = false)
    private Boolean autoTransferYn;

    @Column(name = "auto_transfer_day")
    private Integer autoTransferDay;

    @Column(name = "auto_transfer_status", length = 20)
    private String autoTransferStatus;

    @Builder
    private DepositPaymentSchedule(
        Long depositPaymentScheduleId,
        DepositAccount depositAccount,
        Account account,
        Integer installmentNo,
        LocalDate dueDate,
        BigDecimal plannedAmount,
        BigDecimal paidAmount,
        OffsetDateTime paidAt,
        Boolean isPaid,
        Boolean autoTransferYn,
        Integer autoTransferDay,
        String autoTransferStatus
    ) {
        this.depositPaymentScheduleId = depositPaymentScheduleId;
        this.depositAccount = depositAccount;
        this.account = account;
        this.installmentNo = installmentNo;
        this.dueDate = dueDate;
        this.plannedAmount = plannedAmount;
        this.paidAmount = paidAmount;
        this.paidAt = paidAt;
        this.isPaid = isPaid;
        this.autoTransferYn = autoTransferYn;
        this.autoTransferDay = autoTransferDay;
        this.autoTransferStatus = autoTransferStatus;
    }

    public void markPaid(BigDecimal paidAmount, OffsetDateTime paidAt) {
        this.paidAmount = paidAmount;
        this.paidAt = paidAt;
        this.isPaid = true;
        this.autoTransferStatus = Boolean.TRUE.equals(this.autoTransferYn) ? "SUCCESS" : null;
    }

    public void cancel() {
        if (Boolean.TRUE.equals(this.autoTransferYn) && !Boolean.TRUE.equals(this.isPaid)) {
            this.autoTransferStatus = "STOPPED";
        }
    }
}
