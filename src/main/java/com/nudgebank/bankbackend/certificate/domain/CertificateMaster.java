package com.nudgebank.bankbackend.certificate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "certificate_master")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateMaster {

    @Id
    @Column(name = "certificate_id")
    private Long certificateId;

    @Column(name = "certificate_name", length = 200)
    private String certificateName;

    @Column(name = "issuer_name", length = 200)
    private String issuerName;

    @Column(name = "rate_discount")
    private BigDecimal rateDiscount;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
