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
import lombok.Setter;

@Entity
@Table(
    name = "member",
    uniqueConstraints = @UniqueConstraint(columnNames = "id")
)
@Getter
@Setter
@NoArgsConstructor
public class Member {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "member_id")
  private Long id;

  @Column(name = "id", nullable = false, length = 100)
  private String userId;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "password", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "birth")
  private LocalDate birth;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "gender", length = 10)
  private String gender;
}
