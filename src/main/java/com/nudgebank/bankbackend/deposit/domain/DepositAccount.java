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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "deposit_account")
public class DepositAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deposit_account_id")
    private Long depositAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_product_id", nullable = false)
    private DepositProduct depositProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_product_rate_id", nullable = false)
    private DepositProductRate depositProductRate;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "deposit_account_number", nullable = false, length = 30, unique = true)
    private String depositAccountNumber;

    @Column(name = "join_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal joinAmount;

    @Column(name = "current_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "saving_month", nullable = false)
    private Integer savingMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Builder
    private DepositAccount(
        Long depositAccountId,
        DepositProduct depositProduct,
        DepositProductRate depositProductRate,
        Long memberId,
        Account account,
        String depositAccountNumber,
        BigDecimal joinAmount,
        BigDecimal currentBalance,
        BigDecimal interestRate,
        Integer savingMonth,
        LocalDate startDate,
        LocalDate maturityDate,
        String status
    ) {
        this.depositAccountId = depositAccountId;
        this.depositProduct = depositProduct;
        this.depositProductRate = depositProductRate;
        this.memberId = memberId;
        this.account = account;
        this.depositAccountNumber = depositAccountNumber;
        this.joinAmount = joinAmount;
        this.currentBalance = currentBalance;
        this.interestRate = interestRate;
        this.savingMonth = savingMonth;
        this.startDate = startDate;
        this.maturityDate = maturityDate;
        this.status = status;
    }

    public void receivePayment(BigDecimal amount) {
        validatePositiveAmount(amount);
        this.currentBalance = nullSafe(this.currentBalance).add(amount);
    }

    public BigDecimal close(String status) {
        if (nullSafe(this.currentBalance).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("예적금 잔액이 없습니다.");
        }

        BigDecimal withdrawnAmount = this.currentBalance;
        this.currentBalance = BigDecimal.ZERO.setScale(2);
        this.status = status;
        return withdrawnAmount;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("거래 금액은 0보다 커야 합니다.");
        }
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO.setScale(2);
    }
}
