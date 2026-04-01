package com.nudgebank.bankbackend.certificate.dto;

import com.nudgebank.bankbackend.certificate.domain.CertificateVerificationStatus;

import java.time.OffsetDateTime;

public record CertificateMatchResult(
        CertificateVerificationStatus verificationStatus,
        OffsetDateTime verifiedAt
) {
}
