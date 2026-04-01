package com.nudgebank.bankbackend.certificate.repository;

import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateMasterRepository extends JpaRepository<CertificateMaster, Long> {
    Optional<CertificateMaster> findByCertificateIdAndIsActiveTrue(Long certificateId);
}
