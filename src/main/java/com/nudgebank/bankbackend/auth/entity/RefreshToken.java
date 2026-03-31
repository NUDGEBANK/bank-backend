package com.nudgebank.bankbackend.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {
  @Id
  @Column(length = 64)
  private String rid;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false, length = 512)
  private String token;

  @Column(nullable = false)
  private Instant expiresAt;
}
