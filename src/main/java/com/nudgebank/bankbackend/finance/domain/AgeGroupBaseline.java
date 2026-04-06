package com.nudgebank.bankbackend.finance.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "age_group_baseline")
@Getter
@NoArgsConstructor
public class AgeGroupBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "age_baseline_id")
    private Long id;

    @Column(name = "age_group", nullable = false, length = 20, unique = true)
    private String ageGroup;

    @Column(name = "avg_spending", precision = 15, scale = 2)
    private BigDecimal avgSpending;

    @Column(name = "essential_ratio", precision = 5, scale = 4)
    private BigDecimal essentialRatio;

    @Column(name = "risk_ratio", precision = 5, scale = 4)
    private BigDecimal riskRatio;

    @Column(name = "volatility", precision = 15, scale = 2)
    private BigDecimal volatility;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}