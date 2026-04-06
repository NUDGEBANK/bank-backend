package com.nudgebank.bankbackend.certificate.repository;

import com.nudgebank.bankbackend.certificate.domain.CertificateSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateSubmissionRepository extends JpaRepository<CertificateSubmission, Long> {
    boolean existsByLoanApplicationId(Long loanApplicationId);

    boolean existsByMemberIdAndCertificateIdAndVerificationStatus(
            Long memberId,
            Long certificateId,
            String verificationStatus
    );
}
