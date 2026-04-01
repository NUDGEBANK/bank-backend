package com.nudgebank.bankbackend.account.entity;

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
@Table(name = "`계좌`")
@Getter
@Setter
@NoArgsConstructor
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "`계좌ID`")
  private Long id;

  @Column(name = "`멤버ID`", nullable = false)
  private Long memberId;

  @Column(name = "`계좌명`", nullable = false, length = 100)
  private String accountName;

  @Column(name = "`계좌번호`", nullable = false, length = 30, unique = true)
  private String accountNumber;

  @Column(name = "`잔고`", nullable = false, precision = 15, scale = 2)
  private BigDecimal balance;

  @Column(name = "`개설일`", nullable = false)
  private OffsetDateTime openedAt;

  @Column(name = "`보호잔액`", nullable = false)
  private Long protectedBalance;
}
