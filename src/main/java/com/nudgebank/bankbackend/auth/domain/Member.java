package com.nudgebank.bankbackend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(
    name = "member",
    uniqueConstraints = @UniqueConstraint(columnNames = "id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "member_id")
  private Long memberId;

  @Column(name = "id", nullable = false, length = 100)
  private String id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "password", nullable = false, length = 255)
  private String password;

  @Column(name = "birth")
  private LocalDate birth;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "gender", length = 10)
  private String gender;

  private Member(
      Long memberId,
      String id,
      String name,
      String password,
      LocalDate birth,
      OffsetDateTime createdAt,
      String gender
  ) {
    this.memberId = memberId;
    this.id = id;
    this.name = name;
    this.password = password;
    this.birth = birth;
    this.createdAt = createdAt;
    this.gender = gender;
  }

  public static Member create(
      String id,
      String name,
      String password,
      LocalDate birth,
      OffsetDateTime createdAt,
      String gender
  ) {
    return new Member(null, id, name, password, birth, createdAt, gender);
  }
}
