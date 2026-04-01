package com.nudgebank.bankbackend.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "`카드`")
@Getter
@Setter
@NoArgsConstructor
public class Card {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "`카드ID`")
  private Long id;

  @Column(name = "`계좌ID`", nullable = false)
  private Long accountId;

  @Column(name = "`카드번호`", nullable = false, length = 30, unique = true)
  private String cardNumber;

  @Column(name = "`발급일`", nullable = false)
  private OffsetDateTime issuedAt;

  @Column(name = "`유효기간`", nullable = false, length = 5)
  private String validThru;

  @Column(name = "`비밀번호`", nullable = false, length = 100)
  private String password;

  @Column(name = "`CVC`", nullable = false, length = 3)
  private String cvc;

  @Column(name = "`상태`", nullable = false, length = 20)
  private String status;
}
