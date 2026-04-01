package com.nudgebank.bankbackend.card.domain;

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
@Table(name = "`card`")
@Getter
@Setter
@NoArgsConstructor
public class Card {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "`cardID`")
  private Long id;

  @Column(name = "`accountID`", nullable = false)
  private Long accountId;

  @Column(name = "`cardNumber`", nullable = false, length = 30, unique = true)
  private String cardNumber;

  @Column(name = "`issuedAt`", nullable = false)
  private OffsetDateTime issuedAt;

  @Column(name = "`validThru`", nullable = false, length = 5)
  private String validThru;

  @Column(name = "`password`", nullable = false, length = 100)
  private String password;

  @Column(name = "`cvc`", nullable = false, length = 3)
  private String cvc;

  @Column(name = "`status`", nullable = false, length = 20)
  private String status;
}
