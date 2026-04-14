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
import java.time.OffsetDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "deposit_transaction")
public class DepositTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deposit_transaction_id")
    private Long depositTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_account_id", nullable = false)
    private DepositAccount depositAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_payment_schedule_id")
    private DepositPaymentSchedule depositPaymentSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_datetime", nullable = false)
    private OffsetDateTime transactionDatetime;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Builder
    private DepositTransaction(
        Long depositTransactionId,
        DepositAccount depositAccount,
        DepositPaymentSchedule depositPaymentSchedule,
        Account account,
        String transactionType,
        BigDecimal amount,
        OffsetDateTime transactionDatetime,
        String status
    ) {
        this.depositTransactionId = depositTransactionId;
        this.depositAccount = depositAccount;
        this.depositPaymentSchedule = depositPaymentSchedule;
        this.account = account;
        this.transactionType = transactionType;
        this.amount = amount;
        this.transactionDatetime = transactionDatetime;
        this.status = status;
    }
}
