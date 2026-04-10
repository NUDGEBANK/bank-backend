package com.nudgebank.bankbackend.account.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "account_name", nullable = false, length = 100)
  private String accountName;

  @Column(name = "account_number", nullable = false, length = 30, unique = true)
  private String accountNumber;

  @Column(name = "balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal balance;

  @Column(name = "opened_at", nullable = false)
  private OffsetDateTime openedAt;

  @Column(name = "protected_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal protectedBalance;

  private Account(
      Long accountId,
      Long memberId,
      String accountName,
      String accountNumber,
      BigDecimal balance,
      OffsetDateTime openedAt,
      BigDecimal protectedBalance
  ) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.accountName = accountName;
    this.accountNumber = accountNumber;
    this.balance = balance;
    this.openedAt = openedAt;
    this.protectedBalance = protectedBalance;
  }

  public static Account create(
      Long memberId,
      String accountName,
      String accountNumber,
      BigDecimal balance,
      OffsetDateTime openedAt,
      BigDecimal protectedBalance
  ) {
    return new Account(
        null,
        memberId,
        accountName,
        accountNumber,
        balance,
        openedAt,
        protectedBalance
    );
  }
  public void withdraw(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
    }

    if (this.balance == null) {
      throw new IllegalStateException("계좌 잔액 정보가 없습니다.");
    }

    BigDecimal availableBalance = this.balance.subtract(
      this.protectedBalance != null ? this.protectedBalance : BigDecimal.ZERO
    );
    if (availableBalance.compareTo(amount) < 0) {
      throw new IllegalStateException("사용 가능 잔액이 부족합니다.");
    }

    this.balance = this.balance.subtract(amount);
  }

  public void deposit(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("입금 금액은 0보다 커야 합니다.");
    }

    if (this.balance == null) {
      this.balance = BigDecimal.ZERO;
    }

    this.balance = this.balance.add(amount);
  }

  public void updateProtectedBalance(BigDecimal protectedBalance) {
    if (protectedBalance == null) {
      throw new IllegalArgumentException("보호잔액은 필수입니다.");
    }

    if (protectedBalance.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("보호잔액은 0 이상이어야 합니다.");
    }

    if (this.balance == null) {
      throw new IllegalStateException("계좌 잔액 정보가 없습니다.");
    }

    if (protectedBalance.compareTo(this.balance) > 0) {
      throw new IllegalArgumentException("보호잔액은 계좌 잔액보다 클 수 없습니다.");
    }

    this.protectedBalance = protectedBalance;
  }
}
