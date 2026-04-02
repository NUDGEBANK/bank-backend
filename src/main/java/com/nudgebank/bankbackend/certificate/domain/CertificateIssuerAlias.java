package com.nudgebank.bankbackend.certificate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "certificate_issuer_alias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateIssuerAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alias_id")
    private Long aliasId;

    @Column(name = "certificate_id", nullable = false)
    private Long certificateId;

    @Column(name = "issuer_name", length = 200)
    private String issuerName;

    @Column(name = "issuer_name_en", length = 200)
    private String issuerNameEn;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
