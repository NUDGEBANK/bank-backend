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
import lombok.Setter;

@Entity
@Table(name = "`account`")
@Getter
@Setter
@NoArgsConstructor
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "`accountID`")
  private Long id;

  @Column(name = "`memberID`", nullable = false)
  private Long memberId;

  @Column(name = "`accountName`", nullable = false, length = 100)
  private String accountName;

  @Column(name = "`accountNumber`", nullable = false, length = 30, unique = true)
  private String accountNumber;

  @Column(name = "`balance`", nullable = false, precision = 15, scale = 2)
  private BigDecimal balance;

  @Column(name = "`openedAt`", nullable = false)
  private OffsetDateTime openedAt;

  @Column(name = "`protectedBalance`", nullable = false)
  private Long protectedBalance;
}
