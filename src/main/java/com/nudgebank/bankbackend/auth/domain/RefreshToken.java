package com.nudgebank.bankbackend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

  private RefreshToken(String rid, Long memberId, String token, Instant expiresAt) {
    this.rid = rid;
    this.memberId = memberId;
    this.token = token;
    this.expiresAt = expiresAt;
  }

  public static RefreshToken create(String rid, Long memberId, String token, Instant expiresAt) {
    return new RefreshToken(rid, memberId, token, expiresAt);
  }
}
