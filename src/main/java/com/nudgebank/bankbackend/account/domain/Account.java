package com.nudgebank.bankbackend.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

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
}
