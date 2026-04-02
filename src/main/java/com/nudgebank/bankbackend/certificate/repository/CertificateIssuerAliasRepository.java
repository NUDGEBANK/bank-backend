package com.nudgebank.bankbackend.certificate.repository;

import com.nudgebank.bankbackend.certificate.domain.CertificateIssuerAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateIssuerAliasRepository extends JpaRepository<CertificateIssuerAlias, Long> {
    List<CertificateIssuerAlias> findAllByCertificateIdAndIsActiveTrue(Long certificateId);
}
