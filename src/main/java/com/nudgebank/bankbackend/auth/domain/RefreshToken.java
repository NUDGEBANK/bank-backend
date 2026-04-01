package com.nudgebank.bankbackend.auth.domain;

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
  @Column(name = "rid", length = 64)
  private String rid;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "token", nullable = false, length = 512)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;
}
